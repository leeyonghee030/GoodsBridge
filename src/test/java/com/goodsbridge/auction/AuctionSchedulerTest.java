package com.goodsbridge.auction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AuctionScheduler는 1분마다 세 단계를 순서대로 돌리고, 각 단계/각 경매의 실패를 서로 격리한다.
 *  1) 마감 시간이 지난 ACTIVE 경매 종료
 *  2) 패찰자 보증금 환불
 *  3) 잔금 미납 처리
 */
@ExtendWith(MockitoExtension.class)
class AuctionSchedulerTest {
    private static final List<Status> ENDED_STATUSES = List.of(Status.ENDED_SUCCESS, Status.ENDED_FAIL);

    @Mock AuctionRepository auctionRepository;
    @Mock AuctionService auctionService;
    @InjectMocks AuctionScheduler auctionScheduler;

    /** 각 테스트가 필요한 단계만 다시 stub 하도록, 기본값은 "처리할 대상 없음"으로 둔다. */
    @BeforeEach
    void setUp() {
        lenient().when(auctionRepository.findByStatusAndDeadlineBefore(
                eq(Status.ACTIVE), any(LocalDateTime.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        lenient().when(auctionRepository.findIdsPendingLoserRefund(any())).thenReturn(List.of());
        lenient().when(auctionRepository.findIdsUnpaidOverdue(eq(Status.ENDED_SUCCESS), any(LocalDateTime.class)))
            .thenReturn(List.of());
    }

    private Auction auction(Long id) {
        Auction auction = mock(Auction.class);
        when(auction.getId()).thenReturn(id);
        return auction;
    }

    // ===================== 1단계: 자동 마감 =====================

    @Test
    void autoCloseAuctions_continuesWithOtherAuctionsWhenOneFails() {
        Auction a1 = auction(1L);
        Auction a2 = auction(2L);
        // 1번 조회: 두 건 / 2번 조회: 실패한 1번만 ACTIVE로 남음
        when(auctionRepository.findByStatusAndDeadlineBefore(eq(Status.ACTIVE), any(LocalDateTime.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(a1, a2)))
            .thenReturn(new PageImpl<>(List.of(a1)));
        doThrow(new RuntimeException("낙찰 결제를 찾을 수 없습니다.")).when(auctionService).closeExpired(1L);

        auctionScheduler.autoCloseAuctions();

        verify(auctionService).closeExpired(1L);
        verify(auctionService).closeExpired(2L);
        // 실패한 건을 건너뛰어 무한 반복하지 않는다 (각 1회)
        verify(auctionService, times(2)).closeExpired(anyLong());
    }

    @Test
    void autoCloseAuctions_doesNothingWhenNoExpiredAuction() {
        auctionScheduler.autoCloseAuctions();

        verifyNoInteractions(auctionService);
    }

    @Test
    void autoCloseAuctions_keepsQueryingFirstPageUntilNoTargetIsLeft() {
        Auction a1 = auction(1L);
        Auction a2 = auction(2L);
        Auction a3 = auction(3L);
        // 마감된 경매는 ACTIVE 목록에서 빠지므로 매번 첫 페이지를 다시 조회한다
        when(auctionRepository.findByStatusAndDeadlineBefore(eq(Status.ACTIVE), any(LocalDateTime.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(a1, a2)))
            .thenReturn(new PageImpl<>(List.of(a3)))
            .thenReturn(new PageImpl<>(List.of()));

        auctionScheduler.autoCloseAuctions();

        verify(auctionService).closeExpired(1L);
        verify(auctionService).closeExpired(2L);
        verify(auctionService).closeExpired(3L);
        verify(auctionRepository, times(3)).findByStatusAndDeadlineBefore(
            eq(Status.ACTIVE), any(LocalDateTime.class), any(Pageable.class));
    }

    @Test
    void autoCloseAuctions_queriesExpiredAuctionsInIdOrderWithPageSize100() {
        auctionScheduler.autoCloseAuctions();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(auctionRepository).findByStatusAndDeadlineBefore(
            eq(Status.ACTIVE), any(LocalDateTime.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(100, pageable.getPageSize());
        assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("id").getDirection());
    }

    // ===================== 2단계: 패찰자 보증금 환불 =====================

    @Test
    void autoCloseAuctions_refundsLosersThenMarksAuctionRefunded() {
        when(auctionRepository.findIdsPendingLoserRefund(ENDED_STATUSES)).thenReturn(List.of(10L, 11L));

        auctionScheduler.autoCloseAuctions();

        InOrder inOrder = inOrder(auctionService);
        inOrder.verify(auctionService).refundLosers(10L);
        inOrder.verify(auctionService).markLosersRefunded(10L);
        inOrder.verify(auctionService).refundLosers(11L);
        inOrder.verify(auctionService).markLosersRefunded(11L);
    }

    @Test
    void autoCloseAuctions_doesNotMarkRefundedWhenRefundFails_andContinuesWithNextAuction() {
        when(auctionRepository.findIdsPendingLoserRefund(ENDED_STATUSES)).thenReturn(List.of(10L, 11L));
        doThrow(new RuntimeException("PortOne 오류")).when(auctionService).refundLosers(10L);

        auctionScheduler.autoCloseAuctions();

        // 실패한 경매는 환불 완료로 표시하지 않아 다음 주기에 다시 시도된다
        verify(auctionService, never()).markLosersRefunded(10L);
        // 다음 경매는 계속 처리된다
        verify(auctionService).refundLosers(11L);
        verify(auctionService).markLosersRefunded(11L);
    }

    @Test
    void autoCloseAuctions_refundsBidsWhenAdminCancelledAuctionIsPendingRefund() {
        when(auctionRepository.findIdsPendingCancelRefund(Status.CANCELLED)).thenReturn(List.of(40L));

        auctionScheduler.autoCloseAuctions();

        InOrder inOrder = inOrder(auctionService);
        inOrder.verify(auctionService).refundCancelled(40L);
        inOrder.verify(auctionService).markLosersRefunded(40L);
    }

    // ===================== 3단계: 잔금 미납 처리 =====================

    @Test
    void autoCloseAuctions_forfeitsEveryUnpaidOverdueAuction_evenWhenOneFails() {
        when(auctionRepository.findIdsUnpaidOverdue(eq(Status.ENDED_SUCCESS), any(LocalDateTime.class)))
            .thenReturn(List.of(20L, 21L));
        doThrow(new RuntimeException("정산 오류")).when(auctionService).forfeitUnpaid(20L);

        auctionScheduler.autoCloseAuctions();

        verify(auctionService).forfeitUnpaid(20L);
        verify(auctionService).forfeitUnpaid(21L);
    }

    // ===================== 단계 간 격리 =====================

    @Test
    void autoCloseAuctions_stillRunsLaterStepsWhenCloseStepFailsEntirely() {
        when(auctionRepository.findByStatusAndDeadlineBefore(eq(Status.ACTIVE), any(LocalDateTime.class), any(Pageable.class)))
            .thenThrow(new RuntimeException("DB 오류"));
        when(auctionRepository.findIdsPendingLoserRefund(ENDED_STATUSES)).thenReturn(List.of(30L));
        when(auctionRepository.findIdsUnpaidOverdue(eq(Status.ENDED_SUCCESS), any(LocalDateTime.class)))
            .thenReturn(List.of(31L));

        auctionScheduler.autoCloseAuctions();

        verify(auctionService).refundLosers(30L);
        verify(auctionService).forfeitUnpaid(31L);
    }

    @Test
    void autoCloseAuctions_stillRunsForfeitStepWhenRefundStepFailsEntirely() {
        when(auctionRepository.findIdsPendingLoserRefund(ENDED_STATUSES))
            .thenThrow(new RuntimeException("DB 오류"));
        when(auctionRepository.findIdsUnpaidOverdue(eq(Status.ENDED_SUCCESS), any(LocalDateTime.class)))
            .thenReturn(List.of(31L));

        auctionScheduler.autoCloseAuctions();

        verify(auctionService).forfeitUnpaid(31L);
    }
}
