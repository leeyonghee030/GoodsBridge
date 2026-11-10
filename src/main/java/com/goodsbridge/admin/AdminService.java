package com.goodsbridge.admin;

import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    // 선택한 월의 대시보드 데이터를 만들어 반환한다.
    // 월별 통계는 해당 월 기준으로, 처리 대기 현황은 월과 무관하게 현재 값으로 조회한다.
    public AdminResponse getDashboard(YearMonth month) {
        // 조회 기간: 해당 월 1일 00:00 이상 ~ 다음 달 1일 00:00 미만
        // 예를 들면 1월 1일 00시부터 1월 31일 12:59분까지 체크한다.
        LocalDateTime startAt = month.atDay(1).atStartOfDay();
        LocalDateTime endAt = month.plusMonths(1).atDay(1).atStartOfDay();

        // 기본 통계: 전체 회원 수, 해당 월의 결제 건수 / 결제 금액 / 매출
        long userCount = userRepository.count();
        long paymentCount = adminRepository.countPayments(startAt, endAt);
        long paymentAmount = adminRepository.sumPaymentAmount(startAt, endAt);
        long revenue = adminRepository.sumRevenue(startAt, endAt);

        // 월별 추가 통계: 환불액, 가게 정산 금액
        long refundedAmount = adminRepository.sumRefundedAmount(startAt, endAt);
        long settlementAmount = adminRepository.sumSettlementAmount(startAt, endAt);

        // 결제 목적별 금액: 쿼리 결과 행([purpose, 합계])을 Map으로 변환 (조회 순서 유지)
        Map<String, Long> paymentAmountByPurpose = new LinkedHashMap<>();
        for (Object[] row : adminRepository.sumPaymentAmountByPurpose(startAt, endAt)) {
            paymentAmountByPurpose.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        // 위에서 구한 값 + 처리 대기 현황(환불 요청, 진행 중 경매, 잔금 미납, 발송 대기)으로 응답 생성
        return new AdminResponse(
            userCount, paymentCount, paymentAmount, revenue,
            refundedAmount, settlementAmount, paymentAmountByPurpose,
            adminRepository.countPendingRefunds(),
            adminRepository.countActiveAuctions(),
            adminRepository.countUnpaidBalanceAuctions(),
            adminRepository.countPendingShipments()
        );
    }
}
