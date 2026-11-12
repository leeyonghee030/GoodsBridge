package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
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

    // 입찰 여부 판단
    @Transactional
    public BidResponse placeBid(Long auctionId, Long userId, BidRequest request) {
        if (request == null) {
            throw new BadRequestException("입찰 요청이 필요합니다.");
        }
        // 1. 비관적 락으로 경매 조회 (동시성 제어 지점)
        Auction auction = auctionRepository.findByIdLock(auctionId)
            .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        // 2. 가게 주인 본인 입찰 차단
        if (auction.getShop().isOwner(userId)) {
            throw new ForbiddenException("자신이 판매한 상품은 입찰할 수 없습니다.");
        }

        // 3. 상태/마감 검증
        if (auction.getStatus() != Status.ACTIVE
            || !LocalDateTime.now().isBefore(auction.getDeadline())) {
            throw new BadRequestException("진행 중이며 마감되지 않은 경매만 입찰할 수 있습니다.");
        }

        // 4. 현재가 검증
        if (request.getBidAmount() == null || request.getBidAmount() <= auction.getCurrentPrice()) {
            throw new BadRequestException("입찰가는 현재가보다 높아야 합니다.");
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
        return idPage.map(id -> AuctionResponse.from(auctionMap.get(id)));
    }
}
