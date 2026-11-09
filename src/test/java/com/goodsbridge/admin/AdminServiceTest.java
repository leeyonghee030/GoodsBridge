package com.goodsbridge.admin;

import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminRepository adminRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    void getDashboard_returnsAllStatistics() {
        when(userRepository.count()).thenReturn(4L);
        when(adminRepository.countPayments(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(2L);
        when(adminRepository.sumPaymentAmount(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(75000L);
        when(adminRepository.sumRevenue(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(70000L);

        AdminResponse response = adminService.getDashboard(YearMonth.of(2026, 11));

        assertThat(response.getUserCount()).isEqualTo(4L);
        assertThat(response.getPaymentCount()).isEqualTo(2L);
        assertThat(response.getPaymentAmount()).isEqualTo(75000L);
        assertThat(response.getRevenue()).isEqualTo(70000L);
        verify(userRepository).count();
    }

    @Test
    void getDashboard_usesHalfOpenRangeFromFirstDayToFirstDayOfNextMonth() {
        adminService.getDashboard(YearMonth.of(2026, 11));

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(adminRepository).countPayments(startCaptor.capture(), endCaptor.capture());

        // [11/01 00:00, 12/01 00:00) - 11월 30일 23:59:59.999도 포함, 12월 1일 00:00은 제외
        assertThat(startCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(endCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0));
    }

    @Test
    void getDashboard_handlesYearEndBoundary() {
        adminService.getDashboard(YearMonth.of(2026, 12));

        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(adminRepository).sumRevenue(
            org.mockito.ArgumentMatchers.eq(LocalDateTime.of(2026, 12, 1, 0, 0)), endCaptor.capture());

        assertThat(endCaptor.getValue()).isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0));
    }
}
