package com.goodsbridge.admin;

import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    public AdminResponse getDashboard(YearMonth month) {
        LocalDateTime startAt = month.atDay(1).atStartOfDay();
        LocalDateTime endAt = month.plusMonths(1).atDay(1).atStartOfDay();
        long userCount = userRepository.count();
        long paymentCount = adminRepository.countPayments(startAt, endAt);
        long paymentAmount = adminRepository.sumPaymentAmount(startAt, endAt);
        long revenue = adminRepository.sumRevenue(startAt, endAt);
        return new AdminResponse(userCount, paymentCount, paymentAmount, revenue);
    }
}
