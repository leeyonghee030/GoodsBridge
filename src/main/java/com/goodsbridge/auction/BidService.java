package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.notification.Notification;
import com.goodsbridge.notification.NotificationEvent;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService {
    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final ShopRepository shopRepository;
    private final ApplicationEventPublisher publisher;

    // 입찰 (서버가 거절하면 이미 승인된 보증금 PortOne 결제를 자동 취소한다)
    @Transactional
    public BidResponse placeBid(Long auctionId, Long userId, BidRequest request) {
        if (request == null) {
            throw new BadRequestException("입찰 요청이 필요합니다.");
        }
        try {
            return doPlaceBid(auctionId, userId, request);
        } catch (RuntimeException e) {
            // 어떤 사유로 거절되든 서버에 기록되지 않은 결제는 취소한다 (이미 기록된 결제는 취소되지 않는다)
            String paymentId = request.getPaymentId();
            if (paymentId != null && !paymentId.isBlank()) {
                paymentService.cancelUnrecorded(paymentId, "입찰 처리 실패로 인한 자동 취소");
            }
            throw e;
        }
    }

    // 입찰 본 처리
    private BidResponse doPlaceBid(Long auctionId, Long userId, BidRequest request) {
        // 1. 비관적 락으로 경매 조회 (동시성 제어 지점)
        Auction auction = auctionRepository.findByIdLock(auctionId)
            .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        // 2. 가게 주인 본인 입찰 차단 (다른 가게 경매도 가게 계정은 입찰할 수 없다)
        if (auction.getShop().isOwner(userId)) {
            throw new ForbiddenException("자신이 판매한 상품은 입찰할 수 없습니다.");
        }
        if (shopRepository.existsByOwnerUserIdAndDeletedFalse(userId)) {
            throw new ForbiddenException("가게 계정은 경매에 입찰할 수 없습니다.");
        }
        // 관리자 계정은 관리만 하고 입찰하지 않는다
        if (user.getRole() == Role.ADMIN) {
            throw new ForbiddenException("관리자 계정은 경매에 입찰할 수 없습니다.");
        }

        // 3. 상태/마감 검증
        if (auction.getStatus() != Status.ACTIVE
            || !LocalDateTime.now().isBefore(auction.getDeadline())) {
            throw new BadRequestException("진행 중이며 마감되지 않은 경매만 입찰할 수 있습니다.");
        }

        if (auction.getCurrentWinner() != null
            && auction.getCurrentWinner().getId().equals(userId)) {
            throw new BadRequestException("현재 최고 입찰자는 다시 입찰할 수 없습니다.");
        }

        // 4. 현재가 검증: 아직 입찰이 없으면(현재가 = 시작가) 시작가 이상, 이미 입찰이 있으면 현재가보다 높아야 한다
        Integer bidAmount = request.getBidAmount();
        boolean noBidYet = auction.getCurrentWinner() == null;
        if (bidAmount == null
            || (noBidYet ? bidAmount < auction.getCurrentPrice() : bidAmount <= auction.getCurrentPrice())) {
            throw new BadRequestException(bidAmount != null && noBidYet
                ? "첫 입찰은 시작가(" + String.format("%,d", auction.getCurrentPrice()) + "원) 이상이어야 합니다."
                : "입찰가는 현재가보다 높아야 합니다.");
        }

        if (!noBidYet && (long) bidAmount < (long) auction.getCurrentPrice() + Auction.MIN_BID_INCREMENT) {
            throw new BadRequestException("입찰가는 현재가보다 최소 "
                + String.format("%,d", Auction.MIN_BID_INCREMENT) + "원 높아야 합니다.");
        }

        // 5. 최초 입찰 시에만 보증금 검증 (금액 = Auction.depositAmount)
        //    주의: 이 검증은 PortOne(외부 API)을 호출하며 경매 행 락을 쥔 채 실행된다.
        //    외부 서버가 느려지면 같은 경매의 다른 입찰이 대기하므로, 규모가 커지면 락 범위 분리를 검토한다.
        boolean firstBid = !bidRepository.existsByAuctionIdAndUserId(auctionId, userId);
        String depositPaymentId;

        if (firstBid) {
            if (request.getPaymentId() == null || request.getPaymentId().isBlank()) {
                throw new BadRequestException("최초 입찰에는 보증금 결제 ID가 필요합니다.");
            }
            depositPaymentId = paymentService.verify(request.getPaymentId(),
                auction.getDepositAmount(), PaymentPurpose.AUCTION_DEPOSIT, userId);
        } else {
            Bid previousBid = bidRepository.findFirstByAuctionIdAndUserIdOrderByCreatedAtAscIdAsc(auctionId, userId)
                .orElseThrow(() -> new NotFoundException("기존 입찰 보증금을 찾을 수 없습니다."));
            depositPaymentId = previousBid.getDepositPaymentId();
        }

        // 6. 저장 및 현재가 갱신
        Bid bid = bidRepository.save(Bid.builder()
            .auction(auction)
            .user(user)
            .bidAmount(request.getBidAmount())
            .depositPaymentId(depositPaymentId)
            .build());

        User prevWinner = auction.getCurrentWinner(); // ← 85줄 앞
        auction.updateCurrentBid(request.getBidAmount(), user);
        if (prevWinner != null && !prevWinner.getId().equals(userId)) {
            publisher.publishEvent(
                new NotificationEvent(
                    auction.getCurrentWinner().getId(),
                    Notification.Type.AUCTION_OUTBID,
                    "[상위 입찰]",
                    "'" + auction.getItem().getTitle() + "' 경매에서 더 높은 입찰이 발생했습니다. " +
                        "현재 최고 입찰가를 확인해 주세요.",
                    Notification.ReferenceType.AUCTION,
                    auction.getId()
                )
            );
        }

        auction.updateCurrentBid(request.getBidAmount(), user);

        // 최초 입찰인지와 무관하게 현재 1등의 보증금으로 갱신
        auction.setWinningPaymentId(depositPaymentId);
        return BidResponse.from(bid);
    }

    // 입찰 내역 조회
    public Page<BidResponse> getBids(Long auctionId, Pageable pageable) {
        return bidRepository.findBidsByAuctionId(auctionId, pageable).map(BidResponse::from);
    }

    // 내가 참여한 경매
    public Page<AuctionResponse> participateAuction(Long userId, Pageable pageable){
        Page<Long> idPage = bidRepository.findParticipatedAuctionIds(userId, pageable);

        // IN 조회는 순서를 보장하지 않으므로 ID 순서대로 재정렬
        Map<Long, Auction> auctionMap = auctionRepository.findAllById(idPage.getContent()).stream()
            .collect(Collectors.toMap(Auction::getId, Function.identity()));
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        if (!idPage.getContent().isEmpty()) {
            for (Object[] row : bidRepository.countBidsByAuctionIds(idPage.getContent())) {
                counts.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }
        return idPage.map(id -> AuctionResponse.from(auctionMap.get(id)).withBidCount(counts.getOrDefault(id, 0L)));
    }
}
