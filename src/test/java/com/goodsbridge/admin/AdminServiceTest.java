package com.goodsbridge.admin;

import com.goodsbridge._core.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @InjectMocks
    private AdminService adminService;

    // ===== 처리 대기 현황 =====

    @Test
    void getDashboard_returnsPendingCounts() {
        when(adminRepository.countPendingRefunds()).thenReturn(3L);
        when(adminRepository.countPendingShipments()).thenReturn(5L);
        when(adminRepository.countActiveAuctions()).thenReturn(2L);
        when(adminRepository.countEndedAuctions()).thenReturn(9L);
        when(adminRepository.countUnpaidBalanceAuctions()).thenReturn(1L);

        AdminResponse response = adminService.getDashboard("2026-11");

        assertThat(response.getPendingRefundCount()).isEqualTo(3L);
        assertThat(response.getPendingShipmentCount()).isEqualTo(5L);
        assertThat(response.getActiveAuctionCount()).isEqualTo(2L);
        assertThat(response.getEndedAuctionCount()).isEqualTo(9L);
        assertThat(response.getUnpaidBalanceCount()).isEqualTo(1L);
    }

    // ===== 최근 6개월 구성 =====

    @Test
    void getDashboard_returnsSixMonthsOldestFirstEndingWithSelectedMonth() {
        AdminResponse response = adminService.getDashboard("2026-11");

        List<String> labels = response.getMonths().stream()
            .map(AdminResponse.MonthlyDto::getMonth)
            .toList();

        assertThat(labels).containsExactly(
            "2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11");
    }

    @Test
    void getDashboard_crossesYearBoundaryWhenGoingBackSixMonths() {
        AdminResponse response = adminService.getDashboard("2026-02");

        assertThat(response.getMonths().get(0).getMonth()).isEqualTo("2025-09");
        assertThat(response.getMonths().get(5).getMonth()).isEqualTo("2026-02");
    }

    @Test
    void getDashboard_mapsEachMonthValuesInOrder() {
        // 서비스가 오래된 달부터 차례로 조회하므로 1, 2, ... 6이 순서대로 들어간다.
        when(adminRepository.countPayments(any(), any())).thenReturn(1L, 2L, 3L, 4L, 5L, 6L);

        AdminResponse response = adminService.getDashboard("2026-11");

        assertThat(response.getMonths())
            .extracting(AdminResponse.MonthlyDto::getPaymentCount)
            .containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
    }

    // ===== 월 지표 값 =====

    @Test
    void getDashboard_returnsMonthlyMetrics() {
        when(adminRepository.countSignups(any(), any())).thenReturn(7L);
        when(adminRepository.countPayments(any(), any())).thenReturn(12L);
        when(adminRepository.sumRefundedAmount(any(), any())).thenReturn(4000L);
        when(adminRepository.sumSettlementAmount(any(), any())).thenReturn(90000L);
        when(adminRepository.sumPlatformRevenue(any(), any())).thenReturn(30000L);

        AdminResponse.MonthlyDto selected = lastMonth(adminService.getDashboard("2026-11"));

        assertThat(selected.getSignupCount()).isEqualTo(7L);
        assertThat(selected.getPaymentCount()).isEqualTo(12L);
        assertThat(selected.getRefundedAmount()).isEqualTo(4000L);
        assertThat(selected.getSettlementAmount()).isEqualTo(90000L);
        assertThat(selected.getPlatformRevenue()).isEqualTo(30000L);
    }

    @Test
    void getDashboard_platformRevenueDoesNotIncludeShopRevenue() {
        when(adminRepository.sumPlatformRevenue(any(), any())).thenReturn(30000L);
        when(adminRepository.sumShopRevenue(any(), any())).thenReturn(70000L);

        AdminResponse.MonthlyDto selected = lastMonth(adminService.getDashboard("2026-11"));

        assertThat(selected.getPlatformRevenue()).isEqualTo(30000L);
        assertThat(selected.getShopRevenue()).isEqualTo(70000L);
    }

    // ===== 조회 기간 =====

    @Test
    void getDashboard_usesHalfOpenRangeFromFirstDayToFirstDayOfNextMonth() {
        adminService.getDashboard("2026-11");

        // [11/01 00:00, 12/01 00:00) - 11월 30일 23:59:59.999는 포함, 12월 1일 00:00은 제외
        verify(adminRepository).countPayments(
            eq(LocalDateTime.of(2026, 11, 1, 0, 0)),
            eq(LocalDateTime.of(2026, 12, 1, 0, 0)));
    }

    @Test
    void getDashboard_handlesYearEndBoundary() {
        adminService.getDashboard("2026-12");

        verify(adminRepository).sumPlatformRevenue(
            eq(LocalDateTime.of(2026, 12, 1, 0, 0)),
            eq(LocalDateTime.of(2027, 1, 1, 0, 0)));
    }

    @Test
    void getDashboard_countsSignupsWithTimestampRange() {
        adminService.getDashboard("2026-11");

        // User.createdAt이 Timestamp라서 가입자 쿼리만 Timestamp로 넘긴다.
        verify(adminRepository).countSignups(
            eq(Timestamp.valueOf(LocalDateTime.of(2026, 11, 1, 0, 0))),
            eq(Timestamp.valueOf(LocalDateTime.of(2026, 12, 1, 0, 0))));
    }

    // ===== month 파라미터 검증 (컨트롤러에서 서비스로 옮겨온 부분) =====

    @Test
    void getDashboard_usesCurrentMonthWhenMonthIsNull() {
        AdminResponse response = adminService.getDashboard(null);

        assertThat(lastMonth(response).getMonth()).isEqualTo(YearMonth.now().toString());
    }

    @Test
    void getDashboard_usesCurrentMonthWhenMonthIsBlank() {
        AdminResponse response = adminService.getDashboard("  ");

        assertThat(lastMonth(response).getMonth()).isEqualTo(YearMonth.now().toString());
    }

    @Test
    void getDashboard_rejectsInvalidMonthFormat() {
        for (String invalid : List.of("2026-13", "abc", "2026/11", "202611")) {
            assertThatThrownBy(() -> adminService.getDashboard(invalid))
                .as("month = %s", invalid)
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("YYYY-MM");
        }
    }

    @Test
    void getDashboard_doesNotQueryWhenMonthIsInvalid() {
        assertThatThrownBy(() -> adminService.getDashboard("2026-13"))
            .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(adminRepository);
    }

    // 마지막 원소가 조회한 달이다.
    private AdminResponse.MonthlyDto lastMonth(AdminResponse response) {
        return response.getMonths().get(response.getMonths().size() - 1);
    }
}
