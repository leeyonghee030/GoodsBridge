package com.goodsbridge.admin;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private static final int CHART_MONTHS = 6;  // 그래프에 보여줄 개월 수
    private static final int EXPIRING_DAYS = 7; // 만료 임박 기준(일)

    private final AdminRepository adminRepository;

    // 관리자 대시보드 통계 조회
    public AdminResponse getDashboard(String month) {
        YearMonth selected = parseMonth(month);
        List<AdminResponse.MonthlyDto> months = new ArrayList<>();
        for (int i = CHART_MONTHS - 1; i >= 0; i--) {   // 오래된 달부터
            months.add(monthly(selected.minusMonths(i)));
        }

        LocalDate today = LocalDate.now();
        long approved = adminRepository.countApprovedShops();
        long active = adminRepository.countActiveSubscribedShops(today);
        return new AdminResponse(
            adminRepository.countPendingShops(),
            approved,
            active,
            adminRepository.countExpiringShops(today, today.plusDays(EXPIRING_DAYS)),
            approved == 0 ? 0 : Math.round(active * 100.0 / approved),
            months);
    }

    // 월별 구독 결제 내역. 결제 합계는 목록과 같은 값에서 계산해서 화면의 합계와 목록이 어긋나지 않는다.
    public AdminSubscriptionPaymentResponse getSubscriptionPayments(String month) {
        YearMonth selected = parseMonth(month);
        List<AdminSubscriptionPaymentItem> payments = adminRepository.findSubscriptionPayments(
            selected.atDay(1).atStartOfDay(), selected.plusMonths(1).atDay(1).atStartOfDay());

        // 구독과 연결되지 않은 결제는 결제한 사람의 가게 이름으로 채운다
        Set<Long> ownerIds = payments.stream().filter(p -> p.getShopName() == null)
            .map(AdminSubscriptionPaymentItem::getPayerUserId).collect(Collectors.toSet());
        if (!ownerIds.isEmpty()) {
            Map<Long, String> shopNames = new HashMap<>();
            for (Object[] row : adminRepository.findShopNamesByOwners(ownerIds)) {
                shopNames.merge((Long) row[0], (String) row[1], (a, b) -> a + ", " + b);
            }
            payments.stream().filter(p -> p.getShopName() == null)
                .forEach(p -> p.fillShopName(shopNames.get(p.getPayerUserId())));
        }

        long total = payments.stream().mapToLong(AdminSubscriptionPaymentItem::getAmount).sum();
        int paidCount = (int) payments.stream()
            .filter(p -> p.getStatus() != PaymentStatus.CANCELLED).count();
        long refunded = payments.stream().mapToLong(AdminSubscriptionPaymentItem::getRefundedAmount).sum();
        return new AdminSubscriptionPaymentResponse(
            selected.toString(), paidCount, total, refunded, total - refunded, payments);
    }

    // 해당 월의 가입, 구독 결제, 매출, 신규 입점 및 이탈 통계 조회
    private AdminResponse.MonthlyDto monthly(YearMonth month) {
        LocalDateTime startAt = month.atDay(1).atStartOfDay();
        LocalDateTime endAt = month.plusMonths(1).atDay(1).atStartOfDay();

        return new AdminResponse.MonthlyDto(
            month.toString(),
            adminRepository.countSignups(Timestamp.valueOf(startAt), Timestamp.valueOf(endAt)),
            adminRepository.countSubscriptionPayments(startAt, endAt),
            adminRepository.sumSubscriptionRevenue(startAt, endAt),
            adminRepository.countNewShops(startAt, endAt),
            adminRepository.countChurnedShops(startAt, endAt, month.atDay(1), month.plusMonths(1).atDay(1))
        );
    }

    // 조회 월을 YYYY-MM 형식으로 변환하고, 미입력 시 현재 월 반환
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
