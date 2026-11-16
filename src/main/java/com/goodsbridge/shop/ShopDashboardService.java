package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge.exchange.ExchangeRequestRepository;
import com.goodsbridge.exchange.ExchangeRequestStatus;
import com.goodsbridge.settlement.SettlementHistoryType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopDashboardService {

    private static final int CHART_MONTHS = 6;  // 그래프에 보여줄 개월 수
    private static final int MIN_REVENUE_YEAR = 2020;  // 매출 창에서 거슬러 올라갈 수 있는 가장 이른 해

    private final ShopDashboardRepository dashboardRepository;
    private final ShopRepository shopRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;  // 교환 현황은 교환 관리 화면과 같은 쿼리를 읽기만 한다

    public ShopDashboardResponse getDashboard(Long userId, String month) {
        List<Shop> shops = ownedShops(userId);
        List<Long> shopIds = shops.stream().map(Shop::getId).toList();

        YearMonth selected = parseMonth(month);
        List<ShopDashboardResponse.MonthlyDto> months = new ArrayList<>();
        for (int i = CHART_MONTHS - 1; i >= 0; i--) {   // 오래된 달부터
            months.add(monthly(shopIds, selected.minusMonths(i)));
        }

        // 교환: 오늘 방문 예정과 확정 후 방문을 기다리는 건 (가게 교환 관리 화면과 같은 기준)
        LocalDate today = LocalDate.now();
        long todayMeetings = exchangeRequestRepository
            .findShopMeetingsBetween(userId, today.atStartOfDay(), today.plusDays(1).atStartOfDay()).size();
        long waitingExchanges = exchangeRequestRepository
            .countShopExchanges(userId, List.of(ExchangeRequestStatus.CONFIRMED));

        // 구독은 입점이 승인된 가게만 대상이다
        List<Long> approvedIds = shops.stream().filter(s -> s.getStatus() == ShopStatus.APPROVED).map(Shop::getId).toList();
        long activeShops = approvedIds.isEmpty() ? 0 : dashboardRepository.countActiveSubscribedShops(approvedIds, today);
        LocalDate nearestEnd = approvedIds.isEmpty() ? null : dashboardRepository.findNearestSubscriptionEnd(approvedIds, today);

        return new ShopDashboardResponse(
            dashboardRepository.countPendingRefunds(shopIds),
            dashboardRepository.countPendingShipments(shopIds),
            dashboardRepository.countUnpaidBalanceAuctions(shopIds),
            dashboardRepository.countActiveAuctions(shopIds),
            dashboardRepository.countEndedAuctions(shopIds),
            todayMeetings,
            waitingExchanges,
            dashboardRepository.countOpenEvents(shopIds),
            dashboardRepository.sumPendingSettlement(shopIds),
            nearestEnd,
            nearestEnd == null ? null : (int) ChronoUnit.DAYS.between(today, nearestEnd),
            Math.max(approvedIds.size() - activeShops, 0),
            months);
    }

    // 매출 창: 고른 해의 월별 매출과 종류별 내역 (year 생략 시 올해). 올해는 이번 달까지만 보여준다.
    public ShopRevenueResponse getRevenueHistory(Long userId, Integer year) {
        List<Long> shopIds = ownedShopIds(userId);
        int thisYear = YearMonth.now().getYear();
        int selectedYear = year == null ? thisYear : year;
        if (selectedYear < MIN_REVENUE_YEAR || selectedYear > thisYear) {
            throw new BadRequestException("year는 " + MIN_REVENUE_YEAR + "년부터 올해까지만 조회할 수 있습니다.");
        }
        int lastMonth = selectedYear == thisYear ? YearMonth.now().getMonthValue() : 12;
        List<ShopRevenueResponse.MonthDto> months = new ArrayList<>();
        long total = 0;
        for (int m = 1; m <= lastMonth; m++) {
            YearMonth ym = YearMonth.of(selectedYear, m);
            Map<SettlementHistoryType, Long> byType = new EnumMap<>(SettlementHistoryType.class);
            for (Object[] row : dashboardRepository.sumRevenueByType(
                    shopIds, ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay())) {
                byType.put((SettlementHistoryType) row[0], ((Number) row[1]).longValue());
            }
            long purchase = byType.getOrDefault(SettlementHistoryType.PURCHASE_FEE, 0L);
            long exchange = byType.getOrDefault(SettlementHistoryType.EXCHANGE_FEE, 0L);
            long noShow = byType.getOrDefault(SettlementHistoryType.NO_SHOW_FEE, 0L);
            long auction = byType.getOrDefault(SettlementHistoryType.AUCTION_SALE, 0L);
            long event = byType.getOrDefault(SettlementHistoryType.EVENT_FEE, 0L);
            long sum = purchase + exchange + noShow + auction + event;
            total += sum;
            months.add(new ShopRevenueResponse.MonthDto(ym.toString(), sum, purchase, exchange, noShow, auction, event));
        }
        return new ShopRevenueResponse(selectedYear, total, months);
    }

    private List<Shop> ownedShops(Long userId) {
        List<Shop> shops = shopRepository.findAllByOwnerUserIdAndDeletedFalse(userId, PageRequest.of(0, 50))
            .getContent();
        if (shops.isEmpty()) {
            throw new ForbiddenException("가게 사장님만 이용할 수 있습니다.");
        }
        return shops;
    }

    private List<Long> ownedShopIds(Long userId) {
        return ownedShops(userId).stream().map(Shop::getId).toList();
    }

    private ShopDashboardResponse.MonthlyDto monthly(List<Long> shopIds, YearMonth month) {
        LocalDateTime startAt = month.atDay(1).atStartOfDay();
        LocalDateTime endAt = month.plusMonths(1).atDay(1).atStartOfDay();
        return new ShopDashboardResponse.MonthlyDto(
            month.toString(),
            dashboardRepository.countSales(shopIds, startAt, endAt),
            dashboardRepository.sumRevenue(shopIds, startAt, endAt),
            dashboardRepository.sumRefundedAmount(shopIds, Timestamp.valueOf(startAt), Timestamp.valueOf(endAt)));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("month는 YYYY-MM 형식이어야 합니다.");
        }
    }
}
