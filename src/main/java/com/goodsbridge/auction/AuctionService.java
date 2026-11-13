package com.goodsbridge.auction;

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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService {
    private final AuctionRepository auctionRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final BidRepository bidRepository;
    private final PurchaseRepository purchaseRepository;
    private final SettlementHistoryService settlementHistoryService;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher publisher;

    // 경매 개설
    @Transactional
    public AuctionResponse create(Long userId, Long shopId, AuctionRequest request) {
        request.validate(); // 유효성 검사
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("가게를 찾을 수 없습니다."));
        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("가게의 주인만 경매를 열 수 있습니다.");
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

        // 이미 팔렸거나(SOLD) 판매 중지(CLOSED)된 상품, 삭제된 상품은 경매에 올릴 수 없다.
        if (item.isDeleted() || item.getStatus() != ItemStatus.ON_SALE) {
            throw new BadRequestException("판매 중인 상품만 경매에 등록할 수 있습니다.");
        }

        // 상품이 현재 경매중인 경우
        if (auctionRepository.existsByItemIdAndStatus(item.getId(), Status.ACTIVE)) {
            throw new BadRequestException("이미 진행 중인 경매가 있는 상품입니다.");
        }

        Auction auction = Auction.builder()
            .shop(shop)
            .item(item)
            .startPrice(request.getStartPrice())
            .deadline(request.getDeadline())
            .depositAmount(request.getDepositAmount())
            .build();
        Auction savedAuction = auctionRepository.save(auction);
        return new AuctionResponse(savedAuction);
    }

    // 경매 목록 조회 + 페이징 처리
    public Page<AuctionResponse> findAll(int page, int size, Status status) {
        int pageIndex = Math.max(0, page - 1);
        int validSize = Math.max(1, Math.min(size, 50));
        Pageable pageable = PageRequest.of(pageIndex, validSize, Sort.by(Sort.Direction.DESC, "id"));

        Page<Auction> auctionPage;
        if (status == null) {
            auctionPage = auctionRepository.findAll(pageable);
        } else {
            auctionPage = auctionRepository.findAllByStatus(status, pageable);
        }
        return auctionPage.map(AuctionResponse::new);
    }

    // 경매 상세 조회
    public AuctionResponse findById(Long id) {
        Auction auction = auctionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));
        return new AuctionResponse(auction);
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

    // 경매 취소 - 가게 주인은 경매 입찰자가 없으면 취소한다.
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
        // 입찰이 있는 경우 가게 주인은 경매를 취소할 수 없고, 관리자만 권한이 있다.
        if (!isAdmin && auction.getCurrentWinner() != null) {
            throw new BadRequestException("입찰이 있는 경매는 관리자만 취소할 수 있습니다.");
        }

        auction.cancel();
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
        // 낙찰이면 잔금 결제 기한이 정해진다. 수수료 정산은 잔금 결제가 끝난 뒤(payBalance)에 한다.
        // 패찰자 보증금 환불은 PortOne 호출이라 마감 트랜잭션과 분리해 스케줄러가 처리한다(refundLosers).
        auction.end();

        if (auction.getStatus() == Status.ENDED_SUCCESS){
            publisher.publishEvent(
                new NotificationEvent(
                    auction.getCurrentWinner().getId(),
                    Notification.Type.AUCTION_WIN,
                    auction.getItem() + "이 낙찰되었습니다",
                    auction.getBalanceDueAt() + "까지 " +
                        auction.getBalanceAmount() + "원 결제가 완료 되어야" +
                        "상품을 획득할 수 있습니다",
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

    // 낙찰자 잔금 결제
    @Transactional
    public AuctionResponse payBalance(Long userId, Long auctionId, BalancePayRequest request) {
        if (request == null || request.getPaymentId() == null || request.getPaymentId().isBlank()) {
            throw new BadRequestException("잔금 결제 ID가 필요합니다.");
        }
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
        auction.completeBalance(balancePaymentId);
        auction.getItem().updateStatus(ItemStatus.SOLD);

        purchaseRepository.save(Purchase.builder()
            .item(auction.getItem())
            .user(auction.getCurrentWinner())
            .paymentId(balancePaymentId)
            .deliveryAddress(request.getDeliveryAddress().trim())
            .source(PurchaseSource.AUCTION)
            .amount(auction.getCurrentPrice())
            .build());

        // 가게 정산: 실제로 잔금 결제가 완료된 시점에 보증금 + 잔금인 낙찰가 전액을 기록한다.
        Payment payment = paymentRepository.findByPaymentId(balancePaymentId)
            .orElseThrow(() -> new NotFoundException("잔금 결제를 찾을 수 없습니다."));
        int auctionSettlementAmount = auction.getAuctionSettlementAmount();
        settlementHistoryService.calculate(
            auction.getShop(),
            payment,
            auctionSettlementAmount,
            SettlementHistoryType.AUCTION_SALE
        );
        return new AuctionResponse(auction);
    }

    // 패찰자(낙찰자 제외 입찰자) 보증금 환불.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refundLosers(Long auctionId) {
        Long winnerId = auctionRepository.findWinnerUserId(auctionId);
        if (winnerId == null) {
            return;
        }
        for (String paymentId : bidRepository.findLoserDepositPaymentIds(auctionId, winnerId)) {
            refundDeposit(paymentId, "경매 패찰 보증금 환불");
        }
    }

    // 취소된 경매의 보증금 환불
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refundCancelled(Long auctionId) {
        for (String paymentId : bidRepository.findAllDepositPaymentIds(auctionId)) {
            refundDeposit(paymentId, "경매 취소 보증금 환불");
        }
    }

    // 보증금 결제 1건 환불 (이미 환불됐거나 환불할 금액이 없으면 건너뜀)
    private void refundDeposit(String paymentId, String reason) {
        Payment payment = paymentRepository.findByPaymentId(paymentId).orElse(null);
        if (payment == null) {
            return;
        }
        boolean refundable = payment.isPaid() || payment.getStatus() == PaymentStatus.PARTIAL_CANCELLED;
        if (!refundable || payment.getRefundableAmount() <= 0) {
            return;
        }
        paymentService.refund(paymentId, payment.getRefundableAmount(), reason);
    }

    // 환불이 전부 끝난 경매 표시 (refundLosers 성공 뒤 스케줄러가 호출)
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

        if (auction.getWinningPaymentId() != null) {
            Payment deposit = paymentRepository.findByPaymentId(
                    auction.getWinningPaymentId()
                )
                .orElseThrow(() -> new NotFoundException("낙찰자 보증금 결제를 찾을 수 없습니다."));

            settlementHistoryService.calculate(
                auction.getShop(),
                deposit,
                auction.getDepositAmount(),
                SettlementHistoryType.NO_SHOW_FEE
            );
        }
    }
}
