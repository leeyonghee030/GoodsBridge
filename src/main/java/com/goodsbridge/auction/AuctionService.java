package com.goodsbridge.auction;

import com.goodsbridge.notification.NotificationText;
import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.notification.Notification;
import com.goodsbridge.notification.NotificationEvent;
import com.goodsbridge.payment.*;
import com.goodsbridge.purchase.Purchase;
import com.goodsbridge.purchase.PurchaseRepository;
import com.goodsbridge.purchase.PurchaseSource;
import com.goodsbridge.settlement.SettlementHistory;
import com.goodsbridge.settlement.SettlementHistoryRepository;
import com.goodsbridge.settlement.SettlementHistoryService;
import com.goodsbridge.settlement.SettlementHistoryType;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService {
    private static final int ENDING_SOON_WINDOW_MINUTES = 60;
    private final AuctionRepository auctionRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final BidRepository bidRepository;
    private final PurchaseRepository purchaseRepository;
    private final SettlementHistoryService settlementHistoryService;
    private final SettlementHistoryRepository settlementHistoryRepository;
    private final com.goodsbridge.shop.ShopSubscriptionService shopSubscriptionService;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher publisher;

    // 마감 임박 알림은 경매 행 잠금과 저장 플래그로 중복 발송을 막는다.
    @Transactional
    public void notifyEndingSoon(Long auctionId) {
        Auction auction = auctionRepository.findByIdLock(auctionId)
            .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));
        LocalDateTime now = LocalDateTime.now();
        if (auction.getStatus() != Status.ACTIVE
            || auction.isEndingSoonNotified()
            || auction.getDeadline() == null
            || !auction.getDeadline().isAfter(now)
            || auction.getDeadline().isAfter(now.plusMinutes(ENDING_SOON_WINDOW_MINUTES))) {
            return;
        }

        String content = "'" + auction.getItem().getTitle()
            + "' 경매가 1시간 뒤 마감돼요 (현재가 " + NotificationText.won(auction.getCurrentPrice()) + ")";
        for (Long bidderUserId : bidRepository.findBidderUserIds(auctionId)) {
            publisher.publishEvent(new NotificationEvent(
                bidderUserId,
                Notification.Type.AUCTION_ENDING_SOON,
                "경매 마감 임박",
                content,
                Notification.ReferenceType.AUCTION,
                auctionId
            ));
        }
        auction.markEndingSoonNotified();
    }

    // 잔금 결제 기한이 BALANCE_REMINDER_HOURS 시간 안으로 남은 낙찰자에게 한 번 알린다 (경매 행 잠금 + 저장 플래그로 중복 방지)
    public static final int BALANCE_REMINDER_HOURS = 6;

    @Transactional
    public void notifyBalanceDueSoon(Long auctionId) {
        Auction auction = auctionRepository.findByIdLock(auctionId)
            .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));
        LocalDateTime now = LocalDateTime.now();
        if (auction.getStatus() != Status.ENDED_SUCCESS
            || auction.getBalancePaymentId() != null
            || auction.isBalanceReminderNotified()
            || auction.getCurrentWinner() == null
            || auction.getBalanceDueAt() == null
            || !auction.getBalanceDueAt().isAfter(now)
            || auction.getBalanceDueAt().isAfter(now.plusHours(BALANCE_REMINDER_HOURS))) {
            return;
        }
        publisher.publishEvent(new NotificationEvent(
            auction.getCurrentWinner().getId(),
            Notification.Type.AUCTION_REMAINING_PAYMENT,
            "[잔금 결제 기한 임박]",
            "'" + auction.getItem().getTitle() + "' 낙찰 잔금 " + NotificationText.won(auction.getBalanceAmount()) + "을 "
                + NotificationText.when(auction.getBalanceDueAt())
                + "까지 결제해 주세요. 기한이 지나면 낙찰이 취소되고 보증금은 돌려받을 수 없어요",
            Notification.ReferenceType.AUCTION,
            auctionId
        ));
        auction.markBalanceReminderNotified();
    }

    // 경매 개설
    @Transactional
    public AuctionResponse create(Long userId, Long shopId, AuctionRequest request) {
        if (request == null) {
            throw new BadRequestException("경매 등록 요청 값이 필요합니다.");
        }
        request.validate(); // 유효성 검사
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("가게를 찾을 수 없습니다."));
        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("가게의 주인만 경매를 열 수 있습니다.");
        }
        // 상품 등록·수정과 같이 구독이 활성화된 가게만 경매를 열 수 있다
        if (!shopSubscriptionService.isSubscriptionActive(shopId)) {
            throw new ForbiddenException("구독이 활성화된 가게만 경매를 열 수 있습니다.");
        }
        // 상품 행을 잠가 같은 상품의 동시 경매 개설을 직렬화한다.
        Item item = entityManager.find(
            Item.class,
            request.getItemId(),
            LockModeType.PESSIMISTIC_WRITE
        );

        // 상품잉 없는 경우
        if (item == null) {
            throw new NotFoundException("상품을 찾을 수 없습니다.");
        }

        // 기획상 경매는 해당 가게의 상품만 등록할 수 있다.
        if (!shopId.equals(item.getShopId())) {
            throw new ForbiddenException("해당 가게의 상품만 경매에 등록할 수 있습니다.");
        }

        // 일반 판매 상품은 경매 전에 다른 사람이 살 수 있으므로, 경매 전용(AUCTION)으로 등록한 상품만 경매에 올릴 수 있다.
        if (item.getSaleType() != com.goodsbridge.item.SaleType.AUCTION) {
            throw new BadRequestException("경매 전용으로 등록한 상품만 경매에 올릴 수 있습니다. '경매 상품 등록'으로 먼저 등록해 주세요.");
        }

        // 이미 팔렸거나(SOLD) 판매 중지(CLOSED)된 상품, 삭제된 상품은 경매에 올릴 수 없다.
        if (item.isDeleted() || item.getStatus() != ItemStatus.ON_SALE) {
            throw new BadRequestException("판매 중인 상품만 경매에 등록할 수 있습니다.");
        }

        // 상품이 현재 경매중인 경우
        if (auctionRepository.existsByItemIdAndStatus(item.getId(), Status.ACTIVE)) {
            throw new BadRequestException("이미 진행 중인 경매가 있는 상품입니다.");
        }
        if (auctionRepository.existsUnpaidWinnerByItemId(item.getId())) {
            throw new BadRequestException("낙찰자의 잔금 결제를 기다리는 상품은 경매에 올릴 수 없습니다.");
        }

        Auction auction = Auction.builder()
            .shop(shop)
            .item(item)
            .startPrice(request.getStartPrice())
            .deadline(request.getDeadline())
            .depositAmount(request.getDepositAmount())
            .build();
        auction.changeDescription(request.getDescription());
        Auction savedAuction = auctionRepository.save(auction);
        return new AuctionResponse(savedAuction);
    }

    // 경매 목록 조회 + 페이징 처리
    public Page<AuctionResponse> findAll(int page, int size, Status status) {
        int pageIndex = Math.max(0, page);
        int validSize = Math.max(1, Math.min(size, 50));
        Pageable pageable = PageRequest.of(pageIndex, validSize, Sort.by(Sort.Direction.DESC, "id"));

        Page<Auction> auctionPage;
        if (status == null) {
            auctionPage = auctionRepository.findAll(pageable);
        } else {
            auctionPage = auctionRepository.findAllByStatus(status, pageable);
        }
        return withBidCounts(auctionPage);
    }

    // 가게의 경매 목록 (가게 경매 관리 화면용, 상태는 선택)
    public Page<AuctionResponse> findAllByShop(int page, int size, Status status, Long shopId) {
        int pageIndex = Math.max(0, page);
        int validSize = Math.max(1, Math.min(size, 50));
        Pageable pageable = PageRequest.of(pageIndex, validSize, Sort.by(Sort.Direction.DESC, "id"));
        return withBidCounts(auctionRepository.findAllByShop(shopId, status, pageable));
    }

    // 목록의 각 경매에 입찰 횟수를 붙인다 (경매마다 쿼리하지 않고 한 번에 센다)
    private Page<AuctionResponse> withBidCounts(Page<Auction> auctionPage) {
        java.util.List<Long> ids = auctionPage.getContent().stream().map(Auction::getId).toList();
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : bidRepository.countBidsByAuctionIds(ids)) {
                counts.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }
        return auctionPage.map(a -> new AuctionResponse(a).withBidCount(counts.getOrDefault(a.getId(), 0L)));
    }

    // 경매 수정: 가게 주인만, 입찰이 하나도 없을 때만 (시작가 · 마감 시간 · 보증금)
    @Transactional
    public AuctionResponse update(Long userId, Long auctionId, AuctionUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("경매 수정 요청 값이 필요합니다.");
        }
        request.validate();
        Auction auction = auctionRepository.findByIdLock(auctionId)
            .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));
        if (!auction.getShop().isOwner(userId)) {
            throw new ForbiddenException("가게의 주인만 경매를 수정할 수 있습니다.");
        }
        auction.updateBeforeBids(request.getStartPrice(), request.getDeadline(), request.getDepositAmount(), request.getDescription());
        return new AuctionResponse(auction);
    }

    // 경매 영수증 (낙찰가·낙찰자·결제/배송 정보): 가게 주인, 그리고 그 경매의 낙찰자 본인만 볼 수 있다
    @Transactional(readOnly = true)
    public AuctionReceiptResponse getReceipt(Long userId, Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
            .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));
        boolean isWinner = auction.getCurrentWinner() != null && auction.getCurrentWinner().getId().equals(userId);
        if (!auction.getShop().isOwner(userId) && !isWinner) {
            throw new ForbiddenException("가게 주인 또는 낙찰자만 영수증을 볼 수 있습니다.");
        }
        long bidCount = 0;
        for (Object[] row : bidRepository.countBidsByAuctionIds(java.util.List.of(auctionId))) {
            bidCount = ((Number) row[1]).longValue();
        }
        Purchase purchase = auction.getBalancePaymentId() == null ? null
            : purchaseRepository.findByPaymentId(auction.getBalancePaymentId()).orElse(null);
        return new AuctionReceiptResponse(auction, bidCount, purchase);
    }

    // 가게의 경매 정산 내역: 낙찰 정산 + 낙찰자 미결제로 받은 보증금
    @Transactional(readOnly = true)
    public java.util.List<AuctionSettlementResponse> getShopSettlements(Long userId, Long shopId) {
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("가게를 찾을 수 없습니다."));
        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인 가게의 정산 내역만 조회할 수 있습니다.");
        }
        java.util.List<AuctionSettlementResponse> result = new java.util.ArrayList<>();
        for (SettlementHistory history : settlementHistoryRepository.findAuctionSettlementsByShop(shopId)) {
            String paymentId = history.getPayment().getPaymentId();
            if (history.getType() == SettlementHistoryType.AUCTION_SALE) {
                auctionRepository.findByBalancePaymentId(paymentId).ifPresent(a ->
                    result.add(new AuctionSettlementResponse(a, "AUCTION_SALE", history.getAmount(), history.getCreatedAt())));
            } else if (history.getType() == SettlementHistoryType.AUCTION_DEPOSIT_FORFEIT) {
                auctionRepository.findByWinningPaymentId(paymentId).ifPresent(a ->
                    result.add(new AuctionSettlementResponse(a, "AUCTION_DEPOSIT_FORFEIT", history.getAmount(), history.getCreatedAt())));
            }
        }
        return result;
    }

    // 경매 상세 조회
    public AuctionResponse findById(Long id) {
        Auction auction = auctionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));
        long bidCount = 0;
        for (Object[] row : bidRepository.countBidsByAuctionIds(java.util.List.of(id))) {
            bidCount = ((Number) row[1]).longValue();
        }
        return new AuctionResponse(auction).withBidCount(bidCount);
    }

    // 경매 마감
    @Transactional
    public AuctionResponse close(Long userId, Long id) {
        Auction auction = auctionRepository.findByIdLock(id)
            .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        boolean isOwner = auction.getShop().isOwner(userId);
        boolean isAdmin = user.getRole() == Role.ADMIN;
        // 가게 주인과 관리자만 경매를 마감할 수 있다.
        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("경매를 마감할 권한이 없습니다.");
        }

        closeInternal(auction);
        return new AuctionResponse(auction);
    }

    // 경매 취소
    @Transactional
    public AuctionResponse cancel(Long userId, Long id) {
        Auction auction = auctionRepository.findByIdLock(id)
            .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        boolean isOwner = auction.getShop().isOwner(userId);
        boolean isAdmin = user.getRole() == Role.ADMIN;
        // 가게 주인과 관리자만 경매를 취소할 수 있다.
        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("경매를 취소할 권한이 없습니다.");
        }
        if (auction.getStatus() != Status.ACTIVE) {
            throw new BadRequestException("진행 중인 경매만 취소할 수 있습니다.");
        }
        // 입찰이 있어도 가게 사정으로 취소할 수 있다. 취소하면 모든 입찰자의 보증금이 환불된다 (refundCancelled).

        auction.cancel();

        List<Long> bidderUserIdList = bidRepository.findBidderUserIds(auction.getId());

        for (Long bidderUserId : bidderUserIdList) {
            publisher.publishEvent(
                new NotificationEvent(
                    bidderUserId,
                    Notification.Type.AUCTION_WIN,
                    "[경매 취소]",
                    "'" + auction.getItem().getTitle() + "' 경매가 취소되었습니다. 보증금은 모두 환불됩니다",
                    Notification.ReferenceType.AUCTION,
                    auction.getId()
                )
            );
        }

        return new AuctionResponse(auction);
    }

    // 경매 마감
    private void closeInternal(Auction auction) {
        if (auction.getStatus() != Status.ACTIVE) {
            throw new BadRequestException("진행 중인 경매만 마감할 수 있습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (auction.getDeadline().isAfter(now)) {
            throw new BadRequestException("아직 마감 시간이 지나지 않았습니다.");
        }
        // 낙찰이면 잔금 결제 기한이 정해진다. 정산은 배송 완료 후 구매 확정 시 처리한다.
        // 패찰자 보증금 환불은 PortOne 호출이라 마감 트랜잭션과 분리해 스케줄러가 처리한다(refundLosers).
        auction.end();

        String title = auction.getCurrentWinner() != null ? "[상품 낙찰]" : "[상품 유찰]";
        String content = auction.getCurrentWinner() != null ? "'" + auction.getItem().getTitle() + "' 경매가 " +
            NotificationText.won(auction.getCurrentPrice()) + "에 낙찰되었습니다"
            : "'" + auction.getItem().getTitle() + "' 경매가 유찰되었습니다";

        // 가게 주인
        publisher.publishEvent(
            new NotificationEvent(
                auction.getShop().getOwnerUserId(),
                Notification.Type.AUCTION_WIN,
                title,
                content,
                Notification.ReferenceType.AUCTION,
                auction.getId()
            )
        );

        // 입찰자
        if (auction.getStatus() == Status.ENDED_SUCCESS) {
            publisher.publishEvent(
                new NotificationEvent(
                    auction.getCurrentWinner().getId(),
                    Notification.Type.AUCTION_WIN,
                    "[상품 낙찰]",
                    "'" + auction.getItem().getTitle() + "' 상품에 낙찰되셨습니다!\n" +
                        NotificationText.when(auction.getBalanceDueAt()) + "까지 잔여 결제 금액 " +
                        NotificationText.won(auction.getBalanceAmount()) + "을 결제해 주세요.\n" +
                        "기한 내 결제를 완료하면 상품을 수령할 수 있습니다.",
                    Notification.ReferenceType.AUCTION,
                    auction.getId()
                )
            );
        }
    }

    // 스케줄러가 호출하는 단건 마감.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeExpired(Long auctionId) {
        Auction auction = auctionRepository.findByIdLock(auctionId)
            .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));
        if (auction.getStatus() != Status.ACTIVE) {
            return;
        }
        closeInternal(auction);
    }

    // 낙찰자 잔금 결제 (서버가 거절하면 이미 승인된 잔금 PortOne 결제를 자동 취소한다)
    @Transactional
    public AuctionResponse payBalance(Long userId, Long auctionId, BalancePayRequest request) {
        if (request == null || request.getPaymentId() == null || request.getPaymentId().isBlank()) {
            throw new BadRequestException("잔금 결제 ID가 필요합니다.");
        }
        try {
            return doPayBalance(userId, auctionId, request);
        } catch (RuntimeException e) {
            // 취소 실패는 로그만 남기고 원래 예외를 그대로 던진다 (이미 기록된 결제는 취소되지 않는다)
            paymentService.cancelUnrecorded(request.getPaymentId(), "잔금 결제 처리 실패로 인한 자동 취소");
            throw e;
        }
    }

    // 잔금 결제 본 처리
    private AuctionResponse doPayBalance(Long userId, Long auctionId, BalancePayRequest request) {
        // 마감/미납 처리 스케줄러와 겹치지 않도록 경매 행을 잠근다
        Auction auction = auctionRepository.findByIdLock(auctionId)
            .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));

        if (auction.getStatus() != Status.ENDED_SUCCESS) {
            throw new BadRequestException("낙찰된 경매만 잔금을 결제할 수 있습니다.");
        }
        if (auction.getCurrentWinner() == null || !auction.getCurrentWinner().getId().equals(userId)) {
            throw new ForbiddenException("낙찰자만 잔금을 결제할 수 있습니다.");
        }
        if (auction.isBalancePaid()) {
            throw new BadRequestException("이미 잔금을 결제한 경매입니다.");
        }
        if (auction.isBalanceOverdue(LocalDateTime.now())) {
            throw new BadRequestException("잔금 결제 기한이 지났습니다.");
        }
        int balance = auction.getBalanceAmount();
        if (balance <= 0) {
            throw new BadRequestException("결제할 잔금이 없습니다.");
        }
        request.validateDeliveryAddress();

        String balancePaymentId = paymentService.verify(
            request.getPaymentId(), balance, PaymentPurpose.AUCTION_BALANCE, userId);
        // 결제 검증에 성공했더라도 내부 결제 기록이 없으면 구매를 만들지 않는다.
        paymentRepository.findByPaymentId(balancePaymentId)
            .orElseThrow(() -> new NotFoundException("잔금 결제를 찾을 수 없습니다."));
        auction.completeBalance(balancePaymentId);

        publisher.publishEvent(
            new NotificationEvent(
                auction.getShop().getOwnerUserId(),
                Notification.Type.AUCTION_PAID,
                "[낙찰자 잔금 결제]",
                "낙찰자가 '" + auction.getItem().getTitle() +
                    "' 잔금을 결제했습니다. 배송을 준비해 주세요",
                Notification.ReferenceType.AUCTION,
                auctionId
            )
        );

        auction.getItem().updateStatus(ItemStatus.SOLD);

        purchaseRepository.save(Purchase.builder()
            .item(auction.getItem())
            .user(auction.getCurrentWinner())
            .paymentId(balancePaymentId)
            .deliveryAddress(request.getDeliveryAddress().trim())
            .source(PurchaseSource.AUCTION)
            .amount(auction.getCurrentPrice())
            .build());

        return new AuctionResponse(auction);
    }

    // 패찰자(낙찰자 제외 입찰자) 보증금 환불.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refundLosers(Long auctionId) {
        Long winnerId = auctionRepository.findWinnerUserId(auctionId);
        if (winnerId == null) {
            return;
        }
        // 실제로 환불이 된 건에만 패찰 알림을 보낸다 (환불 실패 후 재시도 때 이미 환불된 건에 알림이 또 가지 않도록)
        refundDeposits(bidRepository.findLoserDepositPaymentIds(auctionId, winnerId), "경매 패찰 보증금 환불",
            payment -> publisher.publishEvent(
                new NotificationEvent(
                    payment.getUser().getId(),
                    Notification.Type.AUCTION_LOST,
                    "[상품 패찰]",
                    " 아쉽게 낙찰되지 않았습니다. 보증금은 환불됩니다",
                    Notification.ReferenceType.AUCTION,
                    auctionId
                )
            ));
    }

    // 취소된 경매의 보증금 환불
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refundCancelled(Long auctionId) {
        refundDeposits(bidRepository.findAllDepositPaymentIds(auctionId), "경매 취소 보증금 환불");
    }

    // 한 건이 실패해도 나머지 환불은 계속하고, 하나라도 실패했으면 첫 예외를 던져 스케줄러가 다음 주기에 재시도하게 한다
    private void refundDeposits(java.util.Collection<String> paymentIds, String reason) {
        refundDeposits(paymentIds, reason, null);
    }

    // onRefunded: 환불이 실제로 일어난 결제에 대해서만 호출된다 (이미 환불됐거나 건너뛴 건은 호출 안 됨)
    private void refundDeposits(java.util.Collection<String> paymentIds, String reason,
                                java.util.function.Consumer<Payment> onRefunded) {
        RuntimeException firstFailure = null;
        for (String paymentId : paymentIds) {
            try {
                Payment refunded = refundDeposit(paymentId, reason);
                if (refunded != null && onRefunded != null) {
                    onRefunded.accept(refunded);
                }
            } catch (RuntimeException e) {
                log.error("경매 보증금 환불 실패 - paymentId={}", paymentId, e);
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    // 보증금 결제 1건 환불 (이미 환불됐거나 환불할 금액이 없으면 건너뜀). 실제로 환불했으면 그 결제를, 건너뛰었으면 null 을 돌려준다.
    private Payment refundDeposit(String paymentId, String reason) {
        Payment payment = paymentRepository.findByPaymentId(paymentId).orElse(null);
        if (payment == null) {
            return null;
        }
        boolean refundable = payment.isPaid() || payment.getStatus() == PaymentStatus.PARTIAL_CANCELLED;
        if (!refundable || payment.getRefundableAmount() <= 0) {
            return null;
        }
        paymentService.refund(paymentId, payment.getRefundableAmount(), reason);
        return payment;
    }

    // 환불이 전부 끝난 경매 표시
    @Transactional
    public void markLosersRefunded(Long auctionId) {
        auctionRepository.findById(auctionId).ifPresent(Auction::markLosersRefunded);
    }

    // 기한 내 잔금 미납 처리
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forfeitUnpaid(Long auctionId) {
        Auction auction = auctionRepository.findByIdLock(auctionId)
            .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));

        // 잠금을 기다리는 사이 낙찰자가 결제했거나 상태가 바뀐 경우는 건드리지 않는다
        if (auction.getStatus() != Status.ENDED_SUCCESS
            || auction.isBalancePaid()
            || !auction.isBalanceOverdue(LocalDateTime.now())) {
            return;
        }
        auction.forfeitUnpaid();

        // 낙찰자
        publisher.publishEvent(
            new NotificationEvent(
                auction.getCurrentWinner().getId(),
                Notification.Type.AUCTION_FORFEITED,
                "[낙찰 취소]",
                "잔금 기한이 지나 낙찰이 취소되었습니다",
                Notification.ReferenceType.AUCTION,
                auctionId
            )
        );

        // 가게
        publisher.publishEvent(
            new NotificationEvent(
                auction.getShop().getOwnerUserId(),
                Notification.Type.AUCTION_FORFEITED,
                "[낙찰 취소]",
                "낙찰자가 기간 내에 잔금을 지급하지 않았으므로, '" +
                 auction.getItem().getTitle() + "' 경매는 무효 처리 됩니다",
                Notification.ReferenceType.AUCTION,
                auctionId
            )
        );

        if (auction.getWinningPaymentId() != null) {
            Payment deposit = paymentRepository.findByPaymentId(
                    auction.getWinningPaymentId()
                )
                .orElseThrow(() -> new NotFoundException("낙찰자 보증금 결제를 찾을 수 없습니다."));

            settlementHistoryService.calculate(
                auction.getShop(),
                deposit,
                auction.getDepositAmount(),
                SettlementHistoryType.AUCTION_DEPOSIT_FORFEIT
            );
        }
    }
}
