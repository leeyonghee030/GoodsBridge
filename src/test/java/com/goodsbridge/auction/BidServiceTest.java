package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(auction.getShop()).thenReturn(shop);
        when(shop.isOwner(2L)).thenReturn(false);
        lenient().when(auction.getStatus()).thenReturn(Status.ACTIVE);
        lenient().when(auction.getDeadline()).thenReturn(LocalDateTime.now().plusMinutes(10));
        lenient().when(auction.getCurrentPrice()).thenReturn(10_000);
        lenient().when(auction.getDepositAmount()).thenReturn(1_000);
    }

    @Test
    void placeBid_savesBidAndUpdatesCurrentBid() {
        when(bidRepository.existsByAuctionIdAndUserId(1L, 2L)).thenReturn(false);
        when(paymentService.verify("payment-1", 1_000, PaymentPurpose.AUCTION_DEPOSIT, 2L))
            .thenReturn("payment-1");
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BidResponse response = bidService.placeBid(1L, 2L, new BidRequest(12_000, "payment-1"));

        assertEquals(12_000, response.getBidAmount());
        verify(paymentService).verify("payment-1", 1_000, PaymentPurpose.AUCTION_DEPOSIT, 2L);
        verify(auction).setWinningPaymentId("payment-1");
        verify(auction).updateCurrentBid(12_000, user);
        verify(bidRepository).save(any(Bid.class));
    }

    @Test
    void placeBid_doesNotVerifyDepositAgainForReturningBidder() {
        when(bidRepository.existsByAuctionIdAndUserId(1L, 2L)).thenReturn(true);
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bidService.placeBid(1L, 2L, new BidRequest(12_000, null));

        verifyNoInteractions(paymentService);
        verify(auction).updateCurrentBid(12_000, user);
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
    void placeBid_rejectsMissingDepositOnFirstBid() {
        BadRequestException exception = assertThrows(BadRequestException.class,
            () -> bidService.placeBid(1L, 2L, new BidRequest(12_000, null)));

        assertEquals("최초 입찰에는 보증금 결제 ID가 필요합니다.", exception.getMessage());
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
}
