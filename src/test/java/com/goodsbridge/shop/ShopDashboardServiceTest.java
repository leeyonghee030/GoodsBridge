package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopDashboardServiceTest {

    private static final Long OWNER_ID = 5L;

    @Mock
    private ShopDashboardRepository dashboardRepository;
    @Mock
    private ShopRepository shopRepository;
    @Mock
    private com.goodsbridge.exchange.ExchangeRequestRepository exchangeRequestRepository;
    @InjectMocks
    private ShopDashboardService service;

    private void ownsShops(Long... shopIds) {
        List<Shop> shops = new java.util.ArrayList<>();
        for (Long id : shopIds) {
            Shop shop = mock(Shop.class);
            when(shop.getId()).thenReturn(id);
            shops.add(shop);
        }
        when(shopRepository.findAllByOwnerUserIdAndDeletedFalse(eq(OWNER_ID), any(Pageable.class)))
            .thenReturn(new PageImpl<>(shops));
    }

    @Test
    void getDashboard_rejectsUserWithoutShop() {
        when(shopRepository.findAllByOwnerUserIdAndDeletedFalse(eq(OWNER_ID), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        assertThatThrownBy(() -> service.getDashboard(OWNER_ID, "2026-11"))
            .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(dashboardRepository);
    }

    @Test
    void getDashboard_queriesOnlyOwnShops() {
        ownsShops(11L, 12L);
        when(dashboardRepository.countPendingRefunds(List.of(11L, 12L))).thenReturn(3L);
        when(dashboardRepository.countPendingShipments(List.of(11L, 12L))).thenReturn(5L);
        when(dashboardRepository.countUnpaidBalanceAuctions(List.of(11L, 12L))).thenReturn(1L);
        when(dashboardRepository.countActiveAuctions(List.of(11L, 12L))).thenReturn(2L);
        when(dashboardRepository.countEndedAuctions(List.of(11L, 12L))).thenReturn(9L);

        ShopDashboardResponse response = service.getDashboard(OWNER_ID, "2026-11");

        assertThat(response.getPendingRefundCount()).isEqualTo(3L);
        assertThat(response.getPendingShipmentCount()).isEqualTo(5L);
        assertThat(response.getUnpaidBalanceCount()).isEqualTo(1L);
        assertThat(response.getActiveAuctionCount()).isEqualTo(2L);
        assertThat(response.getEndedAuctionCount()).isEqualTo(9L);
    }

    @Test
    void getDashboard_returnsSixMonthsOldestFirst() {
        ownsShops(11L);

        ShopDashboardResponse response = service.getDashboard(OWNER_ID, "2026-02");

        assertThat(response.getMonths()).extracting(ShopDashboardResponse.MonthlyDto::getMonth)
            .containsExactly("2025-09", "2025-10", "2025-11", "2025-12", "2026-01", "2026-02");
    }

    @Test
    void getDashboard_mapsMonthlyMetricsAndUsesHalfOpenRange() {
        ownsShops(11L);
        when(dashboardRepository.sumRevenue(any(), any(), any())).thenReturn(80000L);
        when(dashboardRepository.countSales(any(), any(), any())).thenReturn(4L);
        when(dashboardRepository.sumRefundedAmount(any(), any(), any())).thenReturn(5000L);

        ShopDashboardResponse response = service.getDashboard(OWNER_ID, "2026-11");

        ShopDashboardResponse.MonthlyDto selected = response.getMonths().get(5);
        assertThat(selected.getRevenue()).isEqualTo(80000L);
        assertThat(selected.getSaleCount()).isEqualTo(4L);
        assertThat(selected.getRefundedAmount()).isEqualTo(5000L);
        verify(dashboardRepository).sumRevenue(eq(List.of(11L)),
            eq(LocalDateTime.of(2026, 11, 1, 0, 0)), eq(LocalDateTime.of(2026, 12, 1, 0, 0)));
    }

    @Test
    void getDashboard_rejectsInvalidMonth() {
        ownsShops(11L);

        assertThatThrownBy(() -> service.getDashboard(OWNER_ID, "2026-13"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("YYYY-MM");
    }

    // ===== 교환·이벤트·정산 예정·구독 =====

    @Test
    void getDashboard_includesExchangeEventAndPendingSettlement() {
        ownsShops(11L);
        when(exchangeRequestRepository.findShopMeetingsBetween(eq(OWNER_ID), any(), any()))
            .thenReturn(List.of(mock(com.goodsbridge.exchange.ExchangeRequest.class), mock(com.goodsbridge.exchange.ExchangeRequest.class)));
        when(exchangeRequestRepository.countShopExchanges(eq(OWNER_ID), any())).thenReturn(5L);
        when(dashboardRepository.countOpenEvents(List.of(11L))).thenReturn(3L);
        when(dashboardRepository.sumPendingSettlement(List.of(11L))).thenReturn(41000L);

        ShopDashboardResponse response = service.getDashboard(OWNER_ID, "2026-11");

        assertThat(response.getTodayMeetingCount()).isEqualTo(2L);
        assertThat(response.getWaitingExchangeCount()).isEqualTo(5L);
        assertThat(response.getOpenEventCount()).isEqualTo(3L);
        assertThat(response.getPendingSettlementAmount()).isEqualTo(41000L);
    }

    private void ownsApprovedShops(Long... shopIds) {
        List<Shop> shops = new java.util.ArrayList<>();
        for (Long id : shopIds) {
            Shop shop = mock(Shop.class);
            when(shop.getId()).thenReturn(id);
            when(shop.getStatus()).thenReturn(ShopStatus.APPROVED);
            shops.add(shop);
        }
        when(shopRepository.findAllByOwnerUserIdAndDeletedFalse(eq(OWNER_ID), any(Pageable.class)))
            .thenReturn(new PageImpl<>(shops));
    }

    @Test
    void getDashboard_subscriptionShowsNearestEndAndDaysLeft() {
        ownsApprovedShops(11L, 12L);
        java.time.LocalDate end = java.time.LocalDate.now().plusDays(10);
        when(dashboardRepository.countActiveSubscribedShops(eq(List.of(11L, 12L)), any())).thenReturn(1L);
        when(dashboardRepository.findNearestSubscriptionEnd(eq(List.of(11L, 12L)), any())).thenReturn(end);

        ShopDashboardResponse response = service.getDashboard(OWNER_ID, "2026-11");

        assertThat(response.getSubscriptionEndDate()).isEqualTo(end);
        assertThat(response.getSubscriptionDaysLeft()).isEqualTo(10);
        // 승인된 2곳 중 1곳만 구독 중이면 1곳은 구독 중이 아니다
        assertThat(response.getInactiveShopCount()).isEqualTo(1L);
    }

    @Test
    void getDashboard_noActiveSubscriptionHasNoEndDate() {
        ownsApprovedShops(11L);

        ShopDashboardResponse response = service.getDashboard(OWNER_ID, "2026-11");

        assertThat(response.getSubscriptionEndDate()).isNull();
        assertThat(response.getSubscriptionDaysLeft()).isNull();
        assertThat(response.getInactiveShopCount()).isEqualTo(1L);
    }

    @Test
    void getDashboard_unapprovedShopIsNotCountedAsInactiveSubscription() {
        // 입점 승인 전(대기·거절) 가게는 구독할 수 없으므로 '구독 중이 아님'으로 세지 않는다
        ownsShops(11L);

        assertThat(service.getDashboard(OWNER_ID, "2026-11").getInactiveShopCount()).isZero();
    }

    // ===== 매출 창 (연도별, 종류별) =====

    @Test
    void getRevenueHistory_pastYearReturnsAllTwelveMonthsInOrder() {
        ownsShops(11L);

        ShopRevenueResponse response = service.getRevenueHistory(OWNER_ID, 2025);

        assertThat(response.getYear()).isEqualTo(2025);
        assertThat(response.getMonths()).extracting(ShopRevenueResponse.MonthDto::getMonth)
            .containsExactly("2025-01", "2025-02", "2025-03", "2025-04", "2025-05", "2025-06",
                "2025-07", "2025-08", "2025-09", "2025-10", "2025-11", "2025-12");
    }

    @Test
    void getRevenueHistory_currentYearStopsAtThisMonth() {
        ownsShops(11L);

        ShopRevenueResponse response = service.getRevenueHistory(OWNER_ID, null);

        java.time.YearMonth now = java.time.YearMonth.now();
        assertThat(response.getYear()).isEqualTo(now.getYear());
        assertThat(response.getMonths()).hasSize(now.getMonthValue());
        assertThat(response.getMonths().get(response.getMonths().size() - 1).getMonth()).isEqualTo(now.toString());
    }

    @Test
    void getRevenueHistory_splitsAmountByTypeAndSumsMonth() {
        ownsShops(11L);
        when(dashboardRepository.sumRevenueByType(any(), any(), any())).thenReturn(List.of(
            new Object[] { com.goodsbridge.settlement.SettlementHistoryType.PURCHASE_FEE, 50000L },
            new Object[] { com.goodsbridge.settlement.SettlementHistoryType.AUCTION_SALE, 40000L },
            new Object[] { com.goodsbridge.settlement.SettlementHistoryType.EVENT_FEE, 3000L }));

        ShopRevenueResponse.MonthDto january = service.getRevenueHistory(OWNER_ID, 2025).getMonths().get(0);

        assertThat(january.getPurchaseAmount()).isEqualTo(50000L);
        assertThat(january.getAuctionAmount()).isEqualTo(40000L);
        assertThat(january.getEventAmount()).isEqualTo(3000L);
        assertThat(january.getExchangeAmount()).isZero();
        assertThat(january.getNoShowAmount()).isZero();
        // 월 매출 = 종류별 합계 (대시보드 월 매출과 같은 값)
        assertThat(january.getTotalAmount()).isEqualTo(93000L);
    }

    @Test
    void getRevenueHistory_totalIsSumOfTheYearsMonths() {
        ownsShops(11L);
        when(dashboardRepository.sumRevenueByType(any(), any(), any())).thenReturn(List.<Object[]>of(
            new Object[] { com.goodsbridge.settlement.SettlementHistoryType.PURCHASE_FEE, 1000L }));

        assertThat(service.getRevenueHistory(OWNER_ID, 2025).getTotalAmount()).isEqualTo(12000L);
    }

    @Test
    void getRevenueHistory_rejectsFutureAndTooOldYears() {
        ownsShops(11L);
        int nextYear = java.time.YearMonth.now().getYear() + 1;

        assertThatThrownBy(() -> service.getRevenueHistory(OWNER_ID, nextYear))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.getRevenueHistory(OWNER_ID, 1999))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getRevenueHistory_rejectsUserWithoutShop() {
        when(shopRepository.findAllByOwnerUserIdAndDeletedFalse(eq(OWNER_ID), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        assertThatThrownBy(() -> service.getRevenueHistory(OWNER_ID, 2025))
            .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(dashboardRepository);
    }
}
