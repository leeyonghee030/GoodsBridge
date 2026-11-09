package com.goodsbridge.admin;

import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
