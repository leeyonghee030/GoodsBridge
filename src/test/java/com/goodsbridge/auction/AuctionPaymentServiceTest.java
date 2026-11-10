package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.payment.PaymentStatus;
import com.goodsbridge.settlement.SettlementHistoryService;
import com.goodsbridge.settlement.SettlementHistoryType;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 낙찰 후 처리: 잔금 결제(payBalance) / 패찰자 보증금 환불(refundLosers) /
 * 환불 완료 표시(markLosersRefunded) / 잔금 미납 처리(forfeitUnpaid)
 *
 * setUp의 공통 stub은 테스트마다 쓰는 범위가 달라서 모두 lenient로 둔다.
 * (strict stub이면 "안 쓴 stub"으로 UnnecessaryStubbingException이 난다)
 */
@ExtendWith(MockitoExtension.class)
class AuctionPaymentServiceTest {
    @Mock AuctionRepository auctionRepository;
    @Mock ShopRepository shopRepository;
    @Mock ItemRepository itemRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentService paymentService;
    @Mock BidRepository bidRepository;
    @Mock SettlementHistoryService settlementHistoryService;
    @Mock jakarta.persistence.EntityManager entityManager;
    @Mock Auction auction;
    @Mock User winner;
    @Mock Shop shop;
    @Mock Item item;
    @Mock Payment payment;

    @InjectMocks AuctionService auctionService;

    @BeforeEach
    void setUp() {
        lenient().when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));
        lenient().when(auction.getStatus()).thenReturn(Status.ENDED_SUCCESS);
        lenient().when(auction.getCurrentWinner()).thenReturn(winner);
        lenient().when(winner.getId()).thenReturn(2L);
        lenient().when(auction.isBalancePaid()).thenReturn(false);
        lenient().when(auction.isBalanceOverdue(any(LocalDateTime.class))).thenReturn(false);
        lenient().when(auction.getBalanceAmount()).thenReturn(9_000);
        lenient().when(auction.getCurrentPrice()).thenReturn(10_000);
        lenient().when(auction.getShop()).thenReturn(shop);
        lenient().when(auction.getItem()).thenReturn(item);
    }

    // ===================== payBalance =====================

    @Test
    void payBalance_verifiesPayment_marksItemSold_andRecordsFullAuctionSale() {
        when(paymentService.verify("balance-1", 9_000, PaymentPurpose.AUCTION_BALANCE, 2L))
            .thenReturn("balance-1");
        when(paymentRepository.findByPaymentId("balance-1")).thenReturn(Optional.of(payment));

        auctionService.payBalance(2L, 1L, new BalancePayRequest("balance-1"));

        verify(paymentService).verify("balance-1", 9_000, PaymentPurpose.AUCTION_BALANCE, 2L);
        verify(auction).completeBalance("balance-1");
        verify(item).updateStatus(ItemStatus.SOLD);
        verify(settlementHistoryService).calculate(
            shop, payment, 10_000, SettlementHistoryType.AUCTION_SALE);
    }

    @Test
    void payBalance_rejectsOverdueAuctionBeforePortOneVerification() {
        when(auction.isBalanceOverdue(any(LocalDateTime.class))).thenReturn(true);

        assertThrows(BadRequestException.class,
            () -> auctionService.payBalance(2L, 1L, new BalancePayRequest("balance-1")));

        verifyNoInteractions(paymentService);
        verifyNoInteractions(settlementHistoryService);
        verify(auction, never()).completeBalance(any());
    }

    @Test
    void payBalance_rejectsNullRequestOrBlankPaymentIdWithoutTouchingAuction() {
        assertThrows(BadRequestException.class, () -> auctionService.payBalance(2L, 1L, null));
        assertThrows(BadRequestException.class,
            () -> auctionService.payBalance(2L, 1L, new BalancePayRequest(null)));
        assertThrows(BadRequestException.class,
            () -> auctionService.payBalance(2L, 1L, new BalancePayRequest("  ")));

        verify(auctionRepository, never()).findByIdLock(anyLong());
        verifyNoInteractions(paymentService);
    }

    @Test
    void payBalance_rejectsMissingAuction() {
        when(auctionRepository.findByIdLock(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
            () -> auctionService.payBalance(2L, 99L, new BalancePayRequest("balance-1")));

        verifyNoInteractions(paymentService);
    }

    @Test
    void payBalance_rejectsAuctionThatIsNotEndedSuccess() {
        when(auction.getStatus()).thenReturn(Status.ACTIVE);

        assertThrows(BadRequestException.class,
            () -> auctionService.payBalance(2L, 1L, new BalancePayRequest("balance-1")));

        verifyNoInteractions(paymentService);
    }

    @Test
    void payBalance_rejectsUserWhoIsNotTheWinner() {
        assertThrows(ForbiddenException.class,
            () -> auctionService.payBalance(3L, 1L, new BalancePayRequest("balance-1")));

        verifyNoInteractions(paymentService);
        verifyNoInteractions(settlementHistoryService);
    }

    @Test
    void payBalance_rejectsAuctionWithoutWinner() {
        when(auction.getCurrentWinner()).thenReturn(null);

        assertThrows(ForbiddenException.class,
            () -> auctionService.payBalance(2L, 1L, new BalancePayRequest("balance-1")));

        verifyNoInteractions(paymentService);
    }

    @Test
    void payBalance_rejectsAlreadyPaidAuction() {
        when(auction.isBalancePaid()).thenReturn(true);

        assertThrows(BadRequestException.class,
            () -> auctionService.payBalance(2L, 1L, new BalancePayRequest("balance-1")));

        verifyNoInteractions(paymentService);
        verifyNoInteractions(settlementHistoryService);
    }

    @Test
    void payBalance_rejectsWhenThereIsNoBalanceToPay() {
        when(auction.getBalanceAmount()).thenReturn(0);

        assertThrows(BadRequestException.class,
            () -> auctionService.payBalance(2L, 1L, new BalancePayRequest("balance-1")));

        verifyNoInteractions(paymentService);
    }

    @Test
    void payBalance_doesNotChangeAnythingWhenPortOneVerificationFails() {
        when(paymentService.verify("balance-1", 9_000, PaymentPurpose.AUCTION_BALANCE, 2L))
            .thenThrow(new BadRequestException("결제 검증에 실패했습니다."));

        assertThrows(BadRequestException.class,
            () -> auctionService.payBalance(2L, 1L, new BalancePayRequest("balance-1")));

        verify(auction, never()).completeBalance(any());
        verify(item, never()).updateStatus(any());
        verifyNoInteractions(settlementHistoryService);
    }

    @Test
    void payBalance_throwsWhenVerifiedPaymentRecordIsMissing() {
        when(paymentService.verify("balance-1", 9_000, PaymentPurpose.AUCTION_BALANCE, 2L))
            .thenReturn("balance-1");
        when(paymentRepository.findByPaymentId("balance-1")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
            () -> auctionService.payBalance(2L, 1L, new BalancePayRequest("balance-1")));

        verifyNoInteractions(settlementHistoryService);
    }

    // ===================== refundLosers =====================

    @Test
    void refundLosers_refundsAllRefundableLoserDeposits() {
        Payment loserPayment = mock(Payment.class);
        when(auctionRepository.findWinnerUserId(1L)).thenReturn(2L);
        when(bidRepository.findLoserDepositPaymentIds(1L, 2L))
            .thenReturn(List.of("deposit-loser-1"));
        when(paymentRepository.findByPaymentId("deposit-loser-1"))
            .thenReturn(Optional.of(loserPayment));
        when(loserPayment.isPaid()).thenReturn(true);
        when(loserPayment.getRefundableAmount()).thenReturn(1_000);

        auctionService.refundLosers(1L);

        verify(paymentService).refund(
            "deposit-loser-1", 1_000, "경매 패찰 보증금 환불");
    }

    @Test
    void refundLosers_doesNothingWhenAuctionHasNoWinner() {
        when(auctionRepository.findWinnerUserId(1L)).thenReturn(null);

        auctionService.refundLosers(1L);

        verifyNoInteractions(bidRepository);
        verifyNoInteractions(paymentService);
    }

    @Test
    void refundLosers_skipsMissingNonRefundableAndFullyRefundedPayments_butRefundsPartiallyCancelled() {
        Payment unpaid = mock(Payment.class);
        Payment zeroLeft = mock(Payment.class);
        Payment partial = mock(Payment.class);
        when(auctionRepository.findWinnerUserId(1L)).thenReturn(2L);
        when(bidRepository.findLoserDepositPaymentIds(1L, 2L))
            .thenReturn(List.of("missing", "unpaid", "zero-left", "partial"));
        when(paymentRepository.findByPaymentId("missing")).thenReturn(Optional.empty());
        when(paymentRepository.findByPaymentId("unpaid")).thenReturn(Optional.of(unpaid));
        when(paymentRepository.findByPaymentId("zero-left")).thenReturn(Optional.of(zeroLeft));
        when(paymentRepository.findByPaymentId("partial")).thenReturn(Optional.of(partial));
        when(unpaid.isPaid()).thenReturn(false);
        when(unpaid.getStatus()).thenReturn(PaymentStatus.CANCELLED);
        when(zeroLeft.isPaid()).thenReturn(true);
        when(zeroLeft.getRefundableAmount()).thenReturn(0);
        when(partial.isPaid()).thenReturn(false);
        when(partial.getStatus()).thenReturn(PaymentStatus.PARTIAL_CANCELLED);
        when(partial.getRefundableAmount()).thenReturn(500);

        auctionService.refundLosers(1L);

        verify(paymentService, times(1)).refund(anyString(), anyInt(), anyString());
        verify(paymentService).refund("partial", 500, "경매 패찰 보증금 환불");
    }

    @Test
    void refundLosers_propagatesRefundFailureSoSchedulerCanRetry() {
        Payment loserPayment = mock(Payment.class);
        when(auctionRepository.findWinnerUserId(1L)).thenReturn(2L);
        when(bidRepository.findLoserDepositPaymentIds(1L, 2L)).thenReturn(List.of("deposit-loser-1"));
        when(paymentRepository.findByPaymentId("deposit-loser-1")).thenReturn(Optional.of(loserPayment));
        when(loserPayment.isPaid()).thenReturn(true);
        when(loserPayment.getRefundableAmount()).thenReturn(1_000);
        doThrow(new RuntimeException("PortOne 오류")).when(paymentService)
            .refund("deposit-loser-1", 1_000, "경매 패찰 보증금 환불");

        assertThrows(RuntimeException.class, () -> auctionService.refundLosers(1L));
    }

    // ===================== markLosersRefunded =====================

    @Test
    void markLosersRefunded_marksExistingAuction() {
        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));

        auctionService.markLosersRefunded(1L);

        verify(auction).markLosersRefunded();
    }

    @Test
    void markLosersRefunded_ignoresMissingAuction() {
        when(auctionRepository.findById(9L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> auctionService.markLosersRefunded(9L));
    }

    // ===================== forfeitUnpaid =====================

    @Test
    void forfeitUnpaid_changesStatus_andRecordsWinnerDepositAsNoShowFee() {
        when(auction.isBalancePaid()).thenReturn(false);
        when(auction.isBalanceOverdue(any(LocalDateTime.class))).thenReturn(true);
        when(auction.getWinningPaymentId()).thenReturn("deposit-1");
        when(auction.getDepositAmount()).thenReturn(1_000);
        when(paymentRepository.findByPaymentId("deposit-1")).thenReturn(Optional.of(payment));

        auctionService.forfeitUnpaid(1L);

        verify(auction).forfeitUnpaid();
        verify(settlementHistoryService).calculate(
            shop, payment, 1_000, SettlementHistoryType.NO_SHOW_FEE);
    }

    @Test
    void forfeitUnpaid_doesNothingWhenNotOverdueYet() {
        // setUp 기본값: isBalanceOverdue = false
        auctionService.forfeitUnpaid(1L);

        verify(auction, never()).forfeitUnpaid();
        verifyNoInteractions(settlementHistoryService);
    }

    @Test
    void forfeitUnpaid_doesNothingWhenWinnerAlreadyPaidWhileWaitingForLock() {
        when(auction.isBalancePaid()).thenReturn(true);

        auctionService.forfeitUnpaid(1L);

        verify(auction, never()).forfeitUnpaid();
        verifyNoInteractions(settlementHistoryService);
    }

    @Test
    void forfeitUnpaid_doesNothingWhenAuctionIsNotEndedSuccess() {
        when(auction.getStatus()).thenReturn(Status.ENDED_FAIL);

        auctionService.forfeitUnpaid(1L);

        verify(auction, never()).forfeitUnpaid();
        verifyNoInteractions(settlementHistoryService);
    }

    @Test
    void forfeitUnpaid_skipsSettlementWhenNoWinningPaymentId() {
        when(auction.isBalanceOverdue(any(LocalDateTime.class))).thenReturn(true);
        when(auction.getWinningPaymentId()).thenReturn(null);

        auctionService.forfeitUnpaid(1L);

        verify(auction).forfeitUnpaid();
        verifyNoInteractions(settlementHistoryService);
    }

    @Test
    void forfeitUnpaid_throwsWhenWinnerDepositPaymentIsMissing() {
        when(auction.isBalanceOverdue(any(LocalDateTime.class))).thenReturn(true);
        when(auction.getWinningPaymentId()).thenReturn("deposit-1");
        when(paymentRepository.findByPaymentId("deposit-1")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> auctionService.forfeitUnpaid(1L));

        verifyNoInteractions(settlementHistoryService);
    }

    @Test
    void forfeitUnpaid_throwsWhenAuctionMissing() {
        when(auctionRepository.findByIdLock(9L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> auctionService.forfeitUnpaid(9L));
    }
}
