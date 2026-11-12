package com.goodsbridge.auction;

import com.goodsbridge.user.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AuctionTest {

    // ---------- 생성 ----------

    @Test
    void constructor_initializesCurrentPriceWithStartPriceAndActiveStatus() {
        Auction auction = new Auction(null, null, 10_000, LocalDateTime.now().plusHours(1), 1_000);

        assertEquals(10_000, auction.getStartPrice());
        assertEquals(10_000, auction.getCurrentPrice());
        assertEquals(1_000, auction.getDepositAmount());
        assertEquals(Status.ACTIVE, auction.getStatus());
        assertNull(auction.getCurrentWinner());
        assertFalse(auction.isBalancePaid());
        assertFalse(auction.isLosersRefunded());
    }

    @Test
    void updateCurrentBid_changesCurrentPriceAndWinner() {
        Auction auction = new Auction(null, null, 10_000, LocalDateTime.now().plusHours(1), 1_000);
        User bidder = mock(User.class);

        auction.updateCurrentBid(12_000, bidder);

        assertEquals(12_000, auction.getCurrentPrice());
        assertSame(bidder, auction.getCurrentWinner());
    }

    // ---------- 마감 ----------

    @Test
    void end_withoutWinnerBecomesEndedFailWithoutBalanceDue() {
        Auction auction = new Auction(null, null, 10_000, LocalDateTime.now().minusMinutes(1), 1_000);

        auction.end();

        assertEquals(Status.ENDED_FAIL, auction.getStatus());
        assertNull(auction.getBalanceDueAt());
    }

    @Test
    void end_withWinnerBecomesEndedSuccessAndSetsBalanceDueAt() {
        Auction auction = new Auction(null, null, 10_000, LocalDateTime.now().minusMinutes(1), 1_000);
        auction.updateCurrentBid(15_000, mock(User.class));
        LocalDateTime before = LocalDateTime.now();

        auction.end();

        assertEquals(Status.ENDED_SUCCESS, auction.getStatus());
        assertNotNull(auction.getBalanceDueAt());
        assertFalse(auction.getBalanceDueAt().isBefore(before.plusMinutes(Auction.BALANCE_DUE_MINUTES)));
    }

    // ---------- 잔금 ----------

    @Test
    void getBalanceAmount_isCurrentPriceMinusDeposit() {
        Auction auction = new Auction(null, null, 10_000, LocalDateTime.now().plusHours(1), 1_000);
        auction.updateCurrentBid(15_000, mock(User.class));

        assertEquals(14_000, auction.getBalanceAmount());
    }

    @Test
    void getBalanceAmount_neverNegative() {
        // 보증금이 현재가보다 큰 비정상 데이터여도 0 미만으로 내려가지 않는다
        Auction auction = new Auction(null, null, 500, LocalDateTime.now().plusHours(1), 1_000);

        assertEquals(0, auction.getBalanceAmount());
    }

    @Test
    void getAuctionSettlementAmount_isFullWinningPrice() {
        Auction auction = new Auction(null, null, 10_000, LocalDateTime.now().plusHours(1), 1_000);
        auction.updateCurrentBid(15_000, mock(User.class));

        assertEquals(15_000, auction.getAuctionSettlementAmount());
    }

    @Test
    void completeBalance_marksBalancePaid() {
        Auction auction = new Auction(null, null, 10_000, LocalDateTime.now().plusHours(1), 1_000);
        assertFalse(auction.isBalancePaid());

        auction.completeBalance("balance-1");

        assertTrue(auction.isBalancePaid());
        assertEquals("balance-1", auction.getBalancePaymentId());
    }

    @Test
    void isBalanceOverdue_falseWhenNoDueDate() {
        Auction auction = new Auction(null, null, 10_000, LocalDateTime.now().plusHours(1), 1_000);

        assertFalse(auction.isBalanceOverdue(LocalDateTime.now().plusYears(1)));
    }

    @Test
    void isBalanceOverdue_trueOnlyAfterDueDate() {
        Auction auction = new Auction(null, null, 10_000, LocalDateTime.now().minusMinutes(1), 1_000);
        auction.updateCurrentBid(15_000, mock(User.class));
        auction.end();
        LocalDateTime dueAt = auction.getBalanceDueAt();

        assertFalse(auction.isBalanceOverdue(dueAt.minusSeconds(1)));
        assertFalse(auction.isBalanceOverdue(dueAt));            // 기한 정각은 아직 연체 아님
        assertTrue(auction.isBalanceOverdue(dueAt.plusSeconds(1)));
    }

    @Test
    void forfeitUnpaid_changesStatusToEndedFail() {
        Auction auction = new Auction(null, null, 10_000, LocalDateTime.now().minusMinutes(1), 1_000);
        auction.updateCurrentBid(15_000, mock(User.class));
        auction.end();

        auction.forfeitUnpaid();

        assertEquals(Status.ENDED_FAIL, auction.getStatus());
    }

    @Test
    void markLosersRefunded_setsFlag() {
        Auction auction = new Auction(null, null, 10_000, LocalDateTime.now().plusHours(1), 1_000);

        auction.markLosersRefunded();

        assertTrue(auction.isLosersRefunded());
    }
}
