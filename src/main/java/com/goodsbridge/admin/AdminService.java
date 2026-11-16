package com.goodsbridge.admin;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private static final int CHART_MONTHS = 6;  // 그래프에 보여줄 개월 수

    private final AdminRepository adminRepository;

    public AdminResponse getDashboard(String month) {
        YearMonth selected = parseMonth(month);
        List<AdminResponse.MonthlyDto> months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {              // 5,4,3,2,1,0 → 오래된 달부터
            months.add(monthly(selected.minusMonths(i)));
        }
        return new AdminResponse(
            adminRepository.countPendingRefunds(),
            adminRepository.countPendingShipments(),
            adminRepository.countActiveAuctions(),
            adminRepository.countEndedAuctions(),
            adminRepository.countUnpaidBalanceAuctions(),
            months);
    }

    // 월 별 지표 6개
    private AdminResponse.MonthlyDto monthly(YearMonth month) {
        LocalDateTime startAt = month.atDay(1).atStartOfDay();
        LocalDateTime endAt = month.plusMonths(1).atDay(1).atStartOfDay();

        return new AdminResponse.MonthlyDto(
          month.toString(),
            adminRepository.countSignups(Timestamp.valueOf(startAt), Timestamp.valueOf(endAt)),
            adminRepository.countPayments(startAt, endAt),
            adminRepository.sumRefundedAmount(startAt, endAt),
            adminRepository.sumSettlementAmount(startAt, endAt),
            adminRepository.sumPlatformRevenue(startAt, endAt),
            adminRepository.sumShopRevenue(startAt, endAt)
        );
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank())
            return YearMonth.now();
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("month는 YYYY-MM 형식이어야 합니다.");
        }
    }

}
