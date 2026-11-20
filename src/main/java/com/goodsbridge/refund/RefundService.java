package com.goodsbridge.refund;

import com.goodsbridge.notification.NotificationText;
import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.auction.Auction;
import com.goodsbridge.auction.AuctionRepository;
import com.goodsbridge.notification.Notification;
import com.goodsbridge.notification.NotificationEvent;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.payment.PaymentStatus;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.purchase.DeliveryStatus;
import com.goodsbridge.purchase.Purchase;
import com.goodsbridge.purchase.PurchaseRepository;
import com.goodsbridge.settlement.SettlementHistoryRepository;
import com.goodsbridge.settlement.SettlementHistoryService;
import com.goodsbridge.settlement.SettlementHistoryType;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.shop.ShopSubscriptionService;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {
    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final SettlementHistoryService settlementHistoryService;
    private final PurchaseRepository purchaseRepository;
    private final ShopSubscriptionService shopSubscriptionService;
    private final AuctionRepository auctionRepository;
    private final SettlementHistoryRepository settlementHistoryRepository;
    private final ApplicationEventPublisher publisher;
    private static final Set<DeliveryStatus> REFUNDABLE_DELIVERY_STATUSES = EnumSet.of(DeliveryStatus.PREPARING, DeliveryStatus.DELIVERED);
    private static final Set<PaymentPurpose> USER_REFUNDABLE_PURPOSES = EnumSet.of(PaymentPurpose.PURCHASE, PaymentPurpose.AUCTION_BALANCE);

    // 환불 요청
    @Transactional
    public RefundResponse.ListDto request(Long userId, RefundRequest request) {
        if (request == null) {
            throw new BadRequestException("환불 요청 값이 필요합니다.");
        }
        request.validate();
        Payment payment = paymentRepository.findByPaymentIdLock(request.getPaymentId())
            .orElseThrow(() -> new NotFoundException("결제 내역을 찾을 수 없습니다."));
        validateNotAuctionPayment(payment);
        if (!payment.isOwner(userId)) {
            throw new ForbiddenException("본인의 결제만 환불 요청할 수 있습니다.");
        }
        if (!USER_REFUNDABLE_PURPOSES.contains(payment.getPurpose())) {
            throw new BadRequestException("구매 또는 경매 낙찰 결제만 환불을 요청할 수 있어요.");
        }
        if (!payment.isPaid() && payment.getStatus() != PaymentStatus.PARTIAL_CANCELLED) {
            throw new BadRequestException("환불 가능한 결제 상태가 아닙니다.");
        }
        if (isAuctionBalance(payment)) {
            validateAuctionFullRefund(payment, request.getRequestedAmount());
        } else if (request.getRequestedAmount() > payment.getRefundableAmount()) {
            throw new BadRequestException("환불 가능 금액을 초과했습니다.");
        }
        if (refundRepository.existsPendingByPaymentId(request.getPaymentId())) {
            throw new BadRequestException("이미 처리 대기 중인 환불 요청이 있습니다.");
        }
        validateNotRejectedBefore(request.getPaymentId());
        if (payment.getPurpose() == PaymentPurpose.PURCHASE) {
            Purchase purchase = purchaseRepository.findByPaymentId(payment.getPaymentId())
                .orElseThrow(() -> new NotFoundException("해당 결제의 구매 내역을 찾을 수 없습니다."));
            if (!REFUNDABLE_DELIVERY_STATUSES.contains(purchase.getDeliveryStatus())) {
                throw new BadRequestException("배송 준비중이거나 배송이 완료된 구매만 환불을 요청할 수 있습니다.");
            }
        } else if (isAuctionBalance(payment)) {
            validateRefundableDelivery(payment);
        }
        User user = userRepository.getReferenceById(userId);
        Refund refund = Refund.builder().payment(payment).user(user).reason(request.getReason())
            .requestedAmount(request.getRequestedAmount())
            .status(Status.REQUESTED).requestedBy(RequestedBy.USER)
            .type(RefundType.PURCHASE)
            .build();

        List<Long> receiveList = userRepository.findIdsByRole(Role.ADMIN);
        receiveList.add(refund.getUser().getId());

        for (Long receiveId : receiveList){
            publisher.publishEvent(
                new NotificationEvent(
                    receiveId,
                    Notification.Type.REFUND_REQUESTED,
                    "[환불 요청]",
                    "환불 요청이 왔습니다 (" + NotificationText.won(request.getRequestedAmount()) + ")",
                    Notification.ReferenceType.REFUND,
                    refund.getId()
                )
            );
        }

        return new RefundResponse.ListDto(refundRepository.save(refund));
    }

    // 관리자 : 환불 요청 거절
    @Transactional
    public void reject(Long adminUserId, Long refundId, String rejectReason) {
        validateAdmin(adminUserId);
        // approve 와 동시에 실행돼도 상태가 덮어써지지 않도록 같은 행 잠금을 사용한다.
        Refund refund = refundRepository.findByIdLock(refundId)
            .orElseThrow(() -> new NotFoundException("환불 요청을 찾을 수 없습니다."));
        if (!refund.isPending()) {
            throw new BadRequestException("대기 중인 환불 요청만 거절할 수 있습니다.");
        }
        rejectRefund(refund, rejectReason, RejectedBy.ADMIN);
    }

    private void rejectRefund(Refund refund, String rejectReason, RejectedBy rejectedBy) {
        refund.reject(rejectReason, rejectedBy);
        publisher.publishEvent(
            new NotificationEvent(
                refund.getUser().getId(),
                Notification.Type.REFUND_REJECTED,
                "[환불 거절]",
                rejectReason,
                Notification.ReferenceType.REFUND,
                refund.getId()
            )
        );
    }

    // 가게 주인: 자기 가게에서 판매된 상품의 환불 요청 거절 (가게가 이미 거절한 건은 거절 사유만 수정)
    // 관리자가 거절한 건은 가게가 사유를 바꾸거나 뒤집을 수 없다.
    @Transactional
    public void rejectByShop(Long userId, Long shopId, Long refundId, String rejectReason) {
        Refund refund = refundRepository.findByIdLock(refundId)
            .orElseThrow(() -> new NotFoundException("환불 요청을 찾을 수 없습니다."));
        validateShopOwnsRefund(userId, shopId, refund);
        if (refund.getStatus() == Status.REJECTED) {
            validateRejectedByShop(refund);
            refund.changeRejectReason(rejectReason);
            return;
        }
        if (!refund.isPending()) {
            throw new BadRequestException("대기 중인 환불 요청만 거절할 수 있습니다.");
        }
        rejectRefund(refund, rejectReason, RejectedBy.SHOP);
    }

    // 가게 주인: 환불 요청 승인. 가게가 실수로 거절한 건(REJECTED)은 다시 승인할 수 있다.
    // 관리자가 거절한 건은 가게가 뒤집을 수 없다. (승인되어 이미 결제가 취소된 환불은 되돌릴 수 없다)
    @Transactional
    public void approveByShop(Long userId, Long shopId, Long refundId) {
        Refund refund = refundRepository.findByIdLock(refundId)
            .orElseThrow(() -> new NotFoundException("환불 요청을 찾을 수 없습니다."));
        validateShopOwnsRefund(userId, shopId, refund);
        if (refund.getStatus() == Status.REJECTED) {
            validateRejectedByShop(refund);
        }
        approveRefund(refund, true);
    }

    // 거절 주체가 가게인 건만 가게가 다시 손댈 수 있다. (주체가 기록되지 않은 이전 데이터는 관리자 거절로 취급)
    private void validateRejectedByShop(Refund refund) {
        if (!refund.isRejectedByShop()) {
            throw new ForbiddenException("관리자가 거절한 환불 요청은 가게에서 변경할 수 없습니다.");
        }
    }

    private void validateShopOwnsRefund(Long userId, Long shopId, Refund refund) {
        Purchase purchase = purchaseRepository.findByPaymentId(refund.getPayment().getPaymentId())
            .orElseThrow(() -> new NotFoundException("해당 환불의 구매 내역을 찾을 수 없습니다."));
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("가게를 찾을 수 없습니다."));
        if (!shop.isOwner(userId) || !shopId.equals(purchase.getItem().getShopId())) {
            throw new ForbiddenException("본인 가게에서 판매된 상품의 환불만 처리할 수 있습니다.");
        }
    }

    // 관리자 승인 여부 확인
    @Transactional
    public void approve(Long refundId, Long adminUserId) {
        validateAdmin(adminUserId);
        Refund refund = refundRepository.findByIdLock(refundId)
            .orElseThrow(() -> new NotFoundException("환불 요청을 찾을 수 없습니다."));
        approveRefund(refund, false);
    }

    private void approveRefund(Refund refund, boolean allowReconsider) {
        boolean reconsider = allowReconsider && refund.getStatus() == Status.REJECTED;
        if (!reconsider && !refund.isPending()) {
            throw new BadRequestException("대기 중인 환불 요청만 환불 승인 신청이 가능합니다.");
        }
        Payment payment = paymentRepository.findByPaymentIdLock(refund.getPayment().getPaymentId())
            .orElseThrow(() -> new NotFoundException("결제 내역을 찾을 수 없습니다."));
        validateNotAuctionPayment(payment);
        if (!payment.isPaid() && payment.getStatus() != PaymentStatus.PARTIAL_CANCELLED) {
            throw new BadRequestException("결제 완료 상태만 환불할 수 있습니다.");
        }
        // 요청 시점에는 배송 전이었어도 승인 전에 발송됐을 수 있으므로 승인할 때 다시 확인한다.
        validateRefundableDelivery(payment);
        boolean auction = isAuctionBalance(payment);
        if (auction) {
            validateAuctionFullRefund(payment, refund.getRequestedAmount());
        } else if (refund.getRequestedAmount() > payment.getRefundableAmount()) {
            throw new BadRequestException("환불 가능 금액을 초과했습니다.");
        }
        // 외부 호출(PortOne 취소)은 되돌릴 수 없으므로 DB 쪽 상태 변경과 정산 기록을 먼저 끝내고 가장 마지막에 호출한다.
        // 앞 단계에서 예외가 나면 PortOne 취소 없이 전체가 롤백된다.
        refund.approve();
        if (auction) {
            refundAuctionAll(payment, refund.getReason());
        } else {
            createSettlementIfNeeded(refund, payment);
            boolean fullRefund = refund.getRequestedAmount().intValue() == payment.getRefundableAmount();   // Integer끼리 ==로 비교하면 안 된다
            reopenItemIfFullyRefunded(payment, fullRefund);
            paymentService.refund(payment.getPaymentId(), refund.getRequestedAmount(), refund.getReason());
        }

        // 환불 승인 알림은 환불 처리(DB 변경과 PortOne 취소)가 끝난 뒤 발행한다
        publisher.publishEvent(
            new NotificationEvent(
                refund.getUser().getId(),
                Notification.Type.REFUND_APPROVED,
                "[환불 승인]",
                "환불 승인이 되었습니다",
                Notification.ReferenceType.REFUND,
                refund.getId()
            )
        );
    }

    // 배송 중(SHIPPED)인 구매는 환불을 처리할 수 없다 (REFUNDABLE_DELIVERY_STATUSES 기준).
    // 구매 내역이 없는 결제(교환 보증금 등)는 검사 대상이 아니므로 그냥 넘어간다.
    private void validateRefundableDelivery(Payment payment) {
        purchaseRepository.findByPaymentId(payment.getPaymentId()).ifPresent(purchase -> {
            if (!REFUNDABLE_DELIVERY_STATUSES.contains(purchase.getDeliveryStatus())) {
                throw new BadRequestException("배송 중인 구매는 환불을 처리할 수 없습니다. 배송이 완료된 뒤 처리해주세요.");
            }
        });
    }

    // 구매 상품을 전액 환불하면 거래를 종료하고 상품을 다시 판매 목록에 노출한다. 이때 구매 내역에 환불 이력을 남긴다.
    private void reopenItemIfFullyRefunded(Payment payment, boolean fullRefund) {
        if (!fullRefund || payment.getPurpose() != PaymentPurpose.PURCHASE) {
            return;
        }
        purchaseRepository.findByPaymentId(payment.getPaymentId()).ifPresent(purchase -> {
            // SOLD 였던 상품만 다시 연다 (판매 종료(CLOSED)한 상품까지 되살리지 않는다)
            Item item = purchase.getItem();
            if (item.getStatus() == ItemStatus.SOLD) {
                // 구독이 만료된 가게의 상품은 다시 팔지 않고 구독으로 판매 중지해 둔다 (재구독하면 다른 상품과 함께 복구된다)
                if (item.getShopId() != null && !shopSubscriptionService.isSubscriptionActive(item.getShopId())) {
                    item.pauseBySubscription();
                } else {
                    item.updateStatus(ItemStatus.ON_SALE);
                }
            }
            purchase.refund();
        });
    }
    private boolean isAuctionBalance(Payment payment) {
        return payment.getPurpose() == PaymentPurpose.AUCTION_BALANCE;
    }

    // 낙찰가 전액 환불 금액 = 잔금 결제의 남은 금액 + 보증금 결제의 남은 금액
    private int auctionRefundable(Payment balance) {
        int total = balance.getRefundableAmount();
        Auction auction = auctionRepository.findByBalancePaymentId(balance.getPaymentId()).orElse(null);
        if (auction != null && auction.getWinningPaymentId() != null) {
            total += paymentRepository.findByPaymentId(auction.getWinningPaymentId())
                .map(Payment::getRefundableAmount).orElse(0);
        }
        return total;
    }

    // 경매 낙찰 상품은 일부만 환불할 수 없고 낙찰가 전액(보증금 포함)만 환불할 수 있다
    private void validateAuctionFullRefund(Payment balance, Integer requestedAmount) {
        if (requestedAmount == null || requestedAmount != auctionRefundable(balance)) {
            throw new BadRequestException("경매 낙찰 상품은 낙찰가 전액(보증금 포함)만 환불할 수 있어요.");
        }
    }

    // 경매 낙찰 전액 환불: 잔금 + 보증금을 모두 환불하고, 이미 기록된 가게 정산(낙찰가 전액)을 취소한다
    // (외부 PortOne 취소는 DB 변경 뒤 마지막에 호출)
    private void refundAuctionAll(Payment balance, String reason) {
        log.info("경매 낙찰 환불로 가게 정산 기록을 취소합니다 - balancePaymentId={}, type={}",
            balance.getPaymentId(), SettlementHistoryType.AUCTION_SALE);
        // 이 삭제 쿼리는 영속성 컨텍스트를 비우므로(clearAutomatically) 가장 먼저 실행한다.
        // 이후에 조회한 엔티티만 변경 감지가 되어 상품 상태와 구매 상태가 저장된다.
        settlementHistoryRepository.deleteByPaymentAndType(balance, SettlementHistoryType.AUCTION_SALE);
        Auction auction = auctionRepository.findByBalancePaymentId(balance.getPaymentId()).orElse(null);
        Payment deposit = auction == null || auction.getWinningPaymentId() == null ? null
            : paymentRepository.findByPaymentIdLock(auction.getWinningPaymentId()).orElse(null);
        if (auction != null) {
            Item item = auction.getItem();
            if (item != null && item.getStatus() == ItemStatus.SOLD) {
                if (item.getShopId() != null && !shopSubscriptionService.isSubscriptionActive(item.getShopId())) {
                    item.pauseBySubscription();
                } else {
                    item.updateStatus(ItemStatus.ON_SALE);
                }
            }
            purchaseRepository.findByPaymentId(balance.getPaymentId()).ifPresent(Purchase::refund);
        }
        int balanceAmount = balance.getRefundableAmount();
        if (balanceAmount > 0) {
            paymentService.refund(balance.getPaymentId(), balanceAmount, reason);
        }
        if (deposit != null && deposit.getRefundableAmount() > 0) {
            paymentService.refund(deposit.getPaymentId(), deposit.getRefundableAmount(), reason);
        }
    }

    /**
     * 교환 체크인에서 사용하는 노쇼 환불 인터페이스.
     * 교환은 Refund 요청 엔티티를 만들지 않고 결제 보증금만 환불하므로
     * 구매 내역을 통해 가게를 찾지 않는다.
     */
    @Transactional
    public void refundAndSettleNoShow(String paymentId, Long shopId, Integer amount, String reason) {
        if (paymentId == null || paymentId.isBlank() || amount == null || amount <= 0) {
            throw new BadRequestException("노쇼 환불 정보가 올바르지 않습니다.");
        }
        Payment payment = paymentRepository.findByPaymentIdLock(paymentId)
            .orElseThrow(() -> new NotFoundException("결제 내역을 찾을 수 없습니다."));
        if (amount > payment.getRefundableAmount()) {
            throw new BadRequestException("환불 가능 금액을 초과했습니다.");
        }
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("정산 대상 가게를 찾을 수 없습니다."));
        // 정산 기록을 먼저 만들고 PortOne 취소는 마지막에 호출한다 (approve()와 같은 이유)
        settlementHistoryService.calculate(shop, payment, amount, SettlementHistoryType.NO_SHOW_FEE);
        paymentService.refund(paymentId, amount, reason);
    }

    /**
     * RefundType.NO_SHOW 요청에 한해 정산을 만든다.
     * 교환 제안 거절 / 교환 취소 / 응답 기한 초과(RefundType.isFeeFree)는 교환 수수료를 받지 않으므로
     * 환불만 처리하고 정산을 만들지 않는다. 사용자/가게 환불 요청(PURCHASE)도 정산 대상이 아니다.
     * 교환 노쇼는 Refund 엔티티 없이 {@link #refundAndSettleNoShow}를 직접 호출한다.
     */
    private void createSettlementIfNeeded(Refund refund, Payment payment) {
        if (refund.getType() == null || refund.getType().isFeeFree()) {
            return;
        }
        if (refund.getType() != RefundType.NO_SHOW) {
            return;
        }
        if (refund.getRequestedAmount() == null || refund.getRequestedAmount() <= 0) {
            return;
        }
        Purchase purchase = purchaseRepository.findByPaymentId(payment.getPaymentId())
            .orElseThrow(() -> new NotFoundException("구매 내역을 찾을 수 없습니다."));
        Long shopId = purchase.getItem().getShopId();
        if (shopId == null) {
            return;
        }
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("정산 대상 가게를 찾을 수 없습니다."));
        settlementHistoryService.calculate(shop, payment, refund.getRequestedAmount(),
            SettlementHistoryType.NO_SHOW_FEE);
    }

    // 거절된 적이 있는 결제는 다시 환불 요청할 수 없다. (승인으로 일부 환불된 결제는 남은 금액 요청 가능)
    private void validateNotRejectedBefore(String paymentId) {
        if (refundRepository.existsRejectedByPaymentId(paymentId)) {
            throw new BadRequestException("환불이 거절된 결제는 다시 환불 요청할 수 없습니다.");
        }
    }

    // 관리자 권환 확인
    private void validateAdmin(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("관리자만 접근할 수 있습니다.");
        }
    }

    // 환불 목록 조회
    public Page<RefundResponse.ListDto> findAllAdmin(Long adminId, Status status, Pageable pageable) {
        validateAdmin(adminId);
        return refundRepository.findAllAdminPage(status, pageable).map(RefundResponse.ListDto::new);
    }

    // 자신의 환불 내역 찾기
    public List<RefundResponse.ListDto> findMine(Long userId) {
        return refundRepository.findAllByUserIdOrderByIdDesc(userId).stream()
            .map(RefundResponse.ListDto::new).toList();
    }

    // 가게 책임 환불: 하자/불량 등으로 가게가 관리자 승인 없이 직접 결제를 환불한다 (전체/부분 환불)
    @Transactional
    public RefundResponse.ListDto refundByShop(Long userId, Long shopId, ShopRefundRequest shopRefundRequest) {
        if (shopRefundRequest == null) {
            throw new BadRequestException("환불 요청 값이 필요합니다.");
        }
        shopRefundRequest.validate();
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("가게를 찾을 수 없습니다."));
        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("자신의 가게에만 접근할 수 있습니다.");
        }
        // 구매자 환불 승인과 동시에 실행돼도 환불 가능 금액이 겹치지 않도록 결제 행을 잠근다
        Payment payment = paymentRepository.findByPaymentIdLock(shopRefundRequest.getPaymentId())
            .orElseThrow(() -> new NotFoundException("결제를 찾을 수 없습니다."));
        Purchase purchase = purchaseRepository.findByPaymentId(payment.getPaymentId())
            .orElseThrow(() -> new NotFoundException("해당 결제의 구매 내역을 찾을 수 없습니다."));
        if (!shopId.equals(purchase.getItem().getShopId())) {
            throw new ForbiddenException("본인 가게에서 판매된 결제만 환불할 수 있습니다.");
        }
        validateNotAuctionPayment(payment);
        if (payment.getStatus() == PaymentStatus.RELEASED) {
            throw new BadRequestException("수령 확인이 끝난 거래는 환불할 수 없습니다.");
        }
        if (!payment.isPaid() && payment.getStatus() != PaymentStatus.PARTIAL_CANCELLED) {
            throw new BadRequestException("환불 가능한 결제 상태가 아닙니다.");
        }
        boolean auction = isAuctionBalance(payment);
        if (auction) {
            validateAuctionFullRefund(payment, shopRefundRequest.getRequestedAmount());
        } else if (shopRefundRequest.getRequestedAmount() > payment.getRefundableAmount()) {
            throw new BadRequestException("환불 가능 금액을 초과했습니다.");
        }
        // 구매자 환불 요청이 처리 대기 중이면 금액이 겹치므로 관리자 처리 후 환불한다
        if (refundRepository.existsPendingByPaymentId(shopRefundRequest.getPaymentId())) {
            throw new BadRequestException("구매자의 환불 요청이 처리 대기 중입니다. 관리자 처리 후 다시 시도해주세요.");
        }
        User user = userRepository.getReferenceById(userId);
        // 가게 책임 환불은 승인 절차 없이 바로 승인(APPROVED) 상태의 환불 이력으로 남긴다
        Refund refund = refundRepository.save(Refund.builder()
            .payment(payment)
            .user(user)
            .reason(shopRefundRequest.getReason())
            .requestedAmount(shopRefundRequest.getRequestedAmount())
            .status(Status.APPROVED)
            .requestedBy(RequestedBy.SHOP)
            // 가게의 하자/불량 환불은 거래 취소이므로 노쇼 정산 대상이 아니다.
            .type(RefundType.PURCHASE)
            .build());
        // 외부 호출(PortOne 취소)은 DB 변경을 끝낸 뒤 마지막에 호출한다.
        if (auction) {
            refundAuctionAll(payment, shopRefundRequest.getReason());
        } else {
            boolean fullRefund = shopRefundRequest.getRequestedAmount().intValue() == payment.getRefundableAmount();
            reopenItemIfFullyRefunded(payment, fullRefund);
            paymentService.refund(payment.getPaymentId(), shopRefundRequest.getRequestedAmount(), shopRefundRequest.getReason());
        }

        publisher.publishEvent(
            new NotificationEvent(
                purchase.getUser().getId(),
                Notification.Type.PURCHASE_CANCELLED_BY_SHOP,
                "[환불]",
                "가게 사정으로 '" + purchase.getItem().getTitle()
                    + "' 주문이 환불됐습니다 (" + NotificationText.won(refund.getRequestedAmount()) + ")",
                Notification.ReferenceType.REFUND,
                refund.getId()
            )
        );
        return new RefundResponse.ListDto(refund);
    }

    // 경매 보증금 결제는 단독 환불 불가 (낙찰 전액 환불 때 함께 처리)
    private void validateNotAuctionPayment(Payment payment) {
        if (payment.getPurpose() == PaymentPurpose.AUCTION_DEPOSIT) {
            throw new BadRequestException("경매 보증금은 낙찰 상품을 전액 환불할 때 함께 환불돼요.");
        }
    }
}
