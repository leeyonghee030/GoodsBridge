package com.goodsbridge.admin;

import com.goodsbridge._core.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.LocalDate;
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

    // ===== 현재 값: 입점·구독 현황 =====

    @Test
    void getDashboard_returnsShopAndSubscriptionCounts() {
        when(adminRepository.countPendingShops()).thenReturn(4L);
        when(adminRepository.countApprovedShops()).thenReturn(10L);
        when(adminRepository.countActiveSubscribedShops(any())).thenReturn(7L);
        when(adminRepository.countExpiringShops(any(), any())).thenReturn(2L);

        AdminResponse response = adminService.getDashboard("2026-11");

        assertThat(response.getPendingShopCount()).isEqualTo(4L);
        assertThat(response.getApprovedShopCount()).isEqualTo(10L);
        assertThat(response.getActiveSubscriptionCount()).isEqualTo(7L);
        assertThat(response.getExpiringSoonCount()).isEqualTo(2L);
        // 승인된 10곳 중 7곳이 구독 중이면 전환율은 70%
        assertThat(response.getSubscriptionRatePercent()).isEqualTo(70L);
    }

    @Test
    void getDashboard_subscriptionRateRoundsToWholePercent() {
        when(adminRepository.countApprovedShops()).thenReturn(57L);
        when(adminRepository.countActiveSubscribedShops(any())).thenReturn(3L);

        // 3 / 57 = 5.26...% -> 5
        assertThat(adminService.getDashboard("2026-11").getSubscriptionRatePercent()).isEqualTo(5L);
    }

    @Test
    void getDashboard_subscriptionRateIsZeroWhenNoApprovedShops() {
        // 승인된 가게가 없어도 0으로 나누지 않는다
        assertThat(adminService.getDashboard("2026-11").getSubscriptionRatePercent()).isZero();
    }

    @Test
    void getDashboard_expiringSoonLooksSevenDaysAhead() {
        adminService.getDashboard("2026-11");

        LocalDate today = LocalDate.now();
        verify(adminRepository).countExpiringShops(eq(today), eq(today.plusDays(7)));
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
        when(adminRepository.countSubscriptionPayments(any(), any())).thenReturn(1L, 2L, 3L, 4L, 5L, 6L);

        AdminResponse response = adminService.getDashboard("2026-11");

        assertThat(response.getMonths())
            .extracting(AdminResponse.MonthlyDto::getSubscriptionCount)
            .containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
    }

    // ===== 월 지표 값 =====

    @Test
    void getDashboard_returnsMonthlyMetrics() {
        when(adminRepository.countSignups(any(), any())).thenReturn(7L);
        when(adminRepository.countSubscriptionPayments(any(), any())).thenReturn(3L);
        when(adminRepository.sumSubscriptionRevenue(any(), any())).thenReturn(30000L);
        when(adminRepository.countNewShops(any(), any())).thenReturn(4L);
        when(adminRepository.countChurnedShops(any(), any(), any(), any())).thenReturn(2L);

        AdminResponse.MonthlyDto selected = lastMonth(adminService.getDashboard("2026-11"));

        assertThat(selected.getSignupCount()).isEqualTo(7L);
        assertThat(selected.getSubscriptionCount()).isEqualTo(3L);
        assertThat(selected.getSubscriptionRevenue()).isEqualTo(30000L);
        assertThat(selected.getNewShopCount()).isEqualTo(4L);
        assertThat(selected.getChurnCount()).isEqualTo(2L);
    }

    @Test
    void getDashboard_churnUsesTheSameMonthForTimeAndDateRanges() {
        adminService.getDashboard("2026-11");

        // 끝난 시각(endedAt)과 종료일(endDate) 두 기준 모두 [11/01, 12/01)
        verify(adminRepository).countChurnedShops(
            eq(LocalDateTime.of(2026, 11, 1, 0, 0)), eq(LocalDateTime.of(2026, 12, 1, 0, 0)),
            eq(LocalDate.of(2026, 11, 1)), eq(LocalDate.of(2026, 12, 1)));
    }

    // ===== 조회 기간 =====

    @Test
    void getDashboard_usesHalfOpenRangeFromFirstDayToFirstDayOfNextMonth() {
        adminService.getDashboard("2026-11");

        // [11/01 00:00, 12/01 00:00) - 11월 30일 23:59:59.999는 포함, 12월 1일 00:00은 제외
        verify(adminRepository).countSubscriptionPayments(
            eq(LocalDateTime.of(2026, 11, 1, 0, 0)),
            eq(LocalDateTime.of(2026, 12, 1, 0, 0)));
    }

    @Test
    void getDashboard_handlesYearEndBoundary() {
        adminService.getDashboard("2026-12");

        verify(adminRepository).sumSubscriptionRevenue(
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

    // ===== month 파라미터 검증 =====

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

    // ===== 구독 결제 내역 =====

    private AdminSubscriptionPaymentItem payment(String paymentId, Long payerId, String shopName, int amount, int refunded) {
        return new AdminSubscriptionPaymentItem(paymentId, payerId, "사장" + payerId, shopName, amount, refunded,
            com.goodsbridge.payment.PaymentStatus.PAID, LocalDateTime.of(2026, 11, 3, 10, 0));
    }

    @Test
    void getSubscriptionPayments_sumsAmountRefundAndNet() {
        when(adminRepository.findSubscriptionPayments(any(), any())).thenReturn(new java.util.ArrayList<>(List.of(
            payment("p1", 1L, "성수점", 30000, 0),
            payment("p2", 2L, "잠실점", 90000, 20000))));

        AdminSubscriptionPaymentResponse response = adminService.getSubscriptionPayments("2026-11");

        assertThat(response.getMonth()).isEqualTo("2026-11");
        assertThat(response.getPaymentCount()).isEqualTo(2L);
        assertThat(response.getTotalAmount()).isEqualTo(120000L);
        assertThat(response.getRefundedAmount()).isEqualTo(20000L);
        assertThat(response.getNetAmount()).isEqualTo(100000L);
    }

    @Test
    void getSubscriptionPayments_usesHalfOpenMonthRange() {
        adminService.getSubscriptionPayments("2026-11");

        verify(adminRepository).findSubscriptionPayments(
            eq(LocalDateTime.of(2026, 11, 1, 0, 0)), eq(LocalDateTime.of(2026, 12, 1, 0, 0)));
    }

    @Test
    void getSubscriptionPayments_fillsShopNameFromPayerWhenNoSubscriptionIsLinked() {
        when(adminRepository.findSubscriptionPayments(any(), any())).thenReturn(new java.util.ArrayList<>(List.of(
            payment("p1", 7L, null, 30000, 0),
            payment("p2", 8L, "연결된 가게", 30000, 0))));
        when(adminRepository.findShopNamesByOwners(any())).thenReturn(List.of(
            new Object[] { 7L, "홍대점" }, new Object[] { 7L, "강남점" }));

        AdminSubscriptionPaymentResponse response = adminService.getSubscriptionPayments("2026-11");

        // 연결된 구독이 없는 결제는 결제자의 가게 이름(여러 곳이면 이어 붙임), 연결된 결제는 그대로
        assertThat(response.getPayments()).extracting(AdminSubscriptionPaymentItem::getShopName)
            .containsExactly("홍대점, 강남점", "연결된 가게");
    }

    @Test
    void getSubscriptionPayments_rejectsInvalidMonthWithoutQuerying() {
        assertThatThrownBy(() -> adminService.getSubscriptionPayments("2026-13"))
            .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(adminRepository);
    }

    // 마지막 원소가 조회한 달이다.
    private AdminResponse.MonthlyDto lastMonth(AdminResponse response) {
        return response.getMonths().get(response.getMonths().size() - 1);
    }
}
