package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.Item;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 이슈 #20 입찰 로직 단위 테스트 (Repository/Payment는 mock).
 * 실제 DB 락을 이용한 동시성 검증은 BidConcurrencyTest에서 한다.
 *
 * setUp의 공통 stub은 테스트마다 쓰는 범위가 달라 모두 lenient로 둔다.
 */
@ExtendWith(MockitoExtension.class)
class BidServiceTest {
    @Mock BidRepository bidRepository;
    @Mock AuctionRepository auctionRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentService paymentService;
    @Mock Auction auction;
    @Mock Shop shop;
    @Mock User user;

    private BidService bidService;

    @BeforeEach
    void setUp() {
        bidService = new BidService(bidRepository, auctionRepository, userRepository, paymentService);
        lenient().when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));
        lenient().when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        lenient().when(auction.getId()).thenReturn(1L);
        lenient().when(auction.getShop()).thenReturn(shop);
        lenient().when(shop.isOwner(2L)).thenReturn(false);
        lenient().when(user.getId()).thenReturn(2L);
        lenient().when(user.getNickname()).thenReturn("입찰자");
        lenient().when(auction.getStatus()).thenReturn(Status.ACTIVE);
        lenient().when(auction.getDeadline()).thenReturn(LocalDateTime.now().plusMinutes(10));
        lenient().when(auction.getCurrentPrice()).thenReturn(10_000);
        lenient().when(auction.getDepositAmount()).thenReturn(1_000);
    }

    // ===================== 정상 입찰 =====================

    @Test
    void placeBid_savesBidAndUpdatesCurrentBid() {
        when(bidRepository.existsByAuctionIdAndUserId(1L, 2L)).thenReturn(false);
        when(paymentService.verify("payment-1", 1_000, PaymentPurpose.AUCTION_DEPOSIT, 2L))
            .thenReturn("payment-1");
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BidResponse response = bidService.placeBid(1L, 2L, new BidRequest(12_000, "payment-1"));

        assertEquals(12_000, response.getBidAmount());
        assertEquals(1L, response.getAuctionId());
        assertEquals(2L, response.getUserId());
        assertEquals("입찰자", response.getNickname());
        // 보증금 금액 = Auction.depositAmount (이슈 #20 체크리스트)
        verify(paymentService).verify("payment-1", 1_000, PaymentPurpose.AUCTION_DEPOSIT, 2L);
        verify(auction).setWinningPaymentId("payment-1");
        verify(auction).updateCurrentBid(12_000, user);
        verify(bidRepository).save(any(Bid.class));
    }

    @Test
    void placeBid_doesNotExtendDeadlineWhenBidIsNearDeadline() {
        when(bidRepository.existsByAuctionIdAndUserId(1L, 2L)).thenReturn(false);
        when(paymentService.verify("payment-1", 1_000, PaymentPurpose.AUCTION_DEPOSIT, 2L))
            .thenReturn("payment-1");
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bidService.placeBid(1L, 2L, new BidRequest(12_000, "payment-1"));

        // 하드 클로징 정책에서는 입찰로 마감 시간을 연장하지 않는다.
    }

    @Test
    void placeBid_doesNotVerifyDepositAgainForReturningBidder() {
        when(bidRepository.existsByAuctionIdAndUserId(1L, 2L)).thenReturn(true);
        Bid previousBid = mock(Bid.class);
        when(previousBid.getDepositPaymentId()).thenReturn("payment-1");
        when(bidRepository.findFirstByAuctionIdAndUserIdOrderByCreatedAtAscIdAsc(1L, 2L))
            .thenReturn(Optional.of(previousBid));
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bidService.placeBid(1L, 2L, new BidRequest(12_000, null));

        verifyNoInteractions(paymentService);
        verify(auction).updateCurrentBid(12_000, user);
        verify(auction).setWinningPaymentId("payment-1");
    }

    @Test
    void placeBid_updatesWinningPaymentIdToReturningBidderWhenTheyTakeTheLead() {
        when(bidRepository.existsByAuctionIdAndUserId(1L, 2L)).thenReturn(true);
        Bid previousBid = mock(Bid.class);
        when(previousBid.getDepositPaymentId()).thenReturn("payment-a");
        when(bidRepository.findFirstByAuctionIdAndUserIdOrderByCreatedAtAscIdAsc(1L, 2L))
            .thenReturn(Optional.of(previousBid));
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bidService.placeBid(1L, 2L, new BidRequest(20_000, null));

        verify(auction).setWinningPaymentId("payment-a");
        verifyNoInteractions(paymentService);
    }

    // ===================== 거부 케이스 =====================

    @Test
    void placeBid_rejectsNullRequest() {
        BadRequestException exception = assertThrows(BadRequestException.class,
            () -> bidService.placeBid(1L, 2L, null));

        assertEquals("입찰 요청이 필요합니다.", exception.getMessage());
        verify(auctionRepository, never()).findByIdLock(anyLong());
    }

    @Test
    void placeBid_rejectsMissingAuction() {
        when(auctionRepository.findByIdLock(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
            () -> bidService.placeBid(99L, 2L, new BidRequest(12_000, "payment-1")));

        verifyNoInteractions(paymentService);
    }

    @Test
    void placeBid_rejectsMissingUser() {
        when(userRepository.findById(3L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
            () -> bidService.placeBid(1L, 3L, new BidRequest(12_000, "payment-1")));

        verifyNoInteractions(paymentService);
        verify(bidRepository, never()).save(any());
    }

    @Test
    void placeBid_rejectsSellerBid() {
        when(shop.isOwner(2L)).thenReturn(true);

        assertThrows(ForbiddenException.class,
            () -> bidService.placeBid(1L, 2L, new BidRequest(12_000, "payment-1")));

        verifyNoInteractions(paymentService);
        verify(bidRepository, never()).save(any());
    }

    @Test
    void placeBid_rejectsAuctionThatIsNotActive() {
        when(auction.getStatus()).thenReturn(Status.ENDED_SUCCESS);

        assertThrows(BadRequestException.class,
            () -> bidService.placeBid(1L, 2L, new BidRequest(12_000, "payment-1")));

        verifyNoInteractions(paymentService);
        verify(bidRepository, never()).save(any());
    }

    @Test
    void placeBid_rejectsBidAfterDeadlineEvenIfStatusIsStillActive() {
        // 스케줄러가 아직 마감 처리하지 못한 짧은 틈
        when(auction.getDeadline()).thenReturn(LocalDateTime.now().minusSeconds(1));

        assertThrows(BadRequestException.class,
            () -> bidService.placeBid(1L, 2L, new BidRequest(12_000, "payment-1")));

        verifyNoInteractions(paymentService);
        verify(bidRepository, never()).save(any());
    }

    @Test
    void placeBid_rejectsBidAtOrBelowCurrentPrice() {
        BadRequestException exception = assertThrows(BadRequestException.class,
            () -> bidService.placeBid(1L, 2L, new BidRequest(10_000, "payment-1")));

        assertEquals("입찰가는 현재가보다 높아야 합니다.", exception.getMessage());
        verifyNoInteractions(paymentService);
        verify(bidRepository, never()).save(any());
    }

    @Test
    void placeBid_rejectsBidBelowCurrentPrice() {
        assertThrows(BadRequestException.class,
            () -> bidService.placeBid(1L, 2L, new BidRequest(9_999, "payment-1")));

        verify(bidRepository, never()).save(any());
    }

    @Test
    void placeBid_rejectsNullBidAmount() {
        BadRequestException exception = assertThrows(BadRequestException.class,
            () -> bidService.placeBid(1L, 2L, new BidRequest(null, "payment-1")));

        assertEquals("입찰가는 현재가보다 높아야 합니다.", exception.getMessage());
        verify(bidRepository, never()).save(any());
    }

    @Test
    void placeBid_rejectsMissingDepositOnFirstBid() {
        BadRequestException exception = assertThrows(BadRequestException.class,
            () -> bidService.placeBid(1L, 2L, new BidRequest(12_000, null)));

        assertEquals("최초 입찰에는 보증금 결제 ID가 필요합니다.", exception.getMessage());
        verifyNoInteractions(paymentService);
        verify(bidRepository, never()).save(any());
    }

    @Test
    void placeBid_rejectsBlankDepositPaymentIdOnFirstBid() {
        assertThrows(BadRequestException.class,
            () -> bidService.placeBid(1L, 2L, new BidRequest(12_000, "   ")));

        verifyNoInteractions(paymentService);
        verify(bidRepository, never()).save(any());
    }

    @Test
    void placeBid_doesNotSaveOrUpdateAuctionWhenDepositVerificationFails() {
        when(paymentService.verify("payment-1", 1_000, PaymentPurpose.AUCTION_DEPOSIT, 2L))
            .thenThrow(new BadRequestException("결제 검증에 실패했습니다."));

        assertThrows(BadRequestException.class,
            () -> bidService.placeBid(1L, 2L, new BidRequest(12_000, "payment-1")));

        verify(bidRepository, never()).save(any());
        verify(auction, never()).updateCurrentBid(any(), any());
        verify(auction, never()).setWinningPaymentId(any());
    }

    @Test
    void placeBid_throwsWhenReturningBidderHasNoPreviousBidRecord() {
        when(bidRepository.existsByAuctionIdAndUserId(1L, 2L)).thenReturn(true);
        when(bidRepository.findFirstByAuctionIdAndUserIdOrderByCreatedAtAscIdAsc(1L, 2L))
            .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
            () -> bidService.placeBid(1L, 2L, new BidRequest(12_000, null)));

        verify(bidRepository, never()).save(any());
    }

    // ===================== 입찰 내역 / 내가 참여한 경매 =====================

    @Test
    void getBids_mapsBidsToResponses() {
        Bid bid = mock(Bid.class);
        when(bid.getId()).thenReturn(100L);
        when(bid.getAuction()).thenReturn(auction);
        when(bid.getUser()).thenReturn(user);
        when(bid.getBidAmount()).thenReturn(12_000);
        when(bid.getCreatedAt()).thenReturn(LocalDateTime.of(2030, 1, 1, 0, 0));
        Pageable pageable = PageRequest.of(0, 10);
        when(bidRepository.findBidsByAuctionId(1L, pageable))
            .thenReturn(new PageImpl<>(List.of(bid), pageable, 1));

        Page<BidResponse> result = bidService.getBids(1L, pageable);

        assertEquals(1, result.getTotalElements());
        BidResponse response = result.getContent().get(0);
        assertEquals(100L, response.getBidId());
        assertEquals(1L, response.getAuctionId());
        assertEquals(2L, response.getUserId());
        assertEquals("입찰자", response.getNickname());
        assertEquals(12_000, response.getBidAmount());
    }

    @Test
    void participateAuction_keepsParticipationOrderEvenIfFindAllByIdIsUnordered() {
        Pageable pageable = PageRequest.of(0, 10);
        Auction first = auctionWithId(5L);
        Auction second = auctionWithId(3L);
        when(bidRepository.findParticipatedAuctionIds(2L, pageable))
            .thenReturn(new PageImpl<>(List.of(5L, 3L), pageable, 2));
        // IN 조회는 순서를 보장하지 않으므로 일부러 뒤집어서 돌려준다
        when(auctionRepository.findAllById(List.of(5L, 3L))).thenReturn(List.of(second, first));

        Page<AuctionResponse> result = bidService.participateAuction(2L, pageable);

        assertEquals(List.of(5L, 3L), result.getContent().stream().map(AuctionResponse::getId).toList());
        assertEquals(2, result.getTotalElements());
    }

    private Auction auctionWithId(Long id) {
        Auction real = new Auction(mock(Shop.class), mock(Item.class), 10_000,
            LocalDateTime.now().plusHours(1), 1_000);
        ReflectionTestUtils.setField(real, "id", id);
        return real;
    }
}
