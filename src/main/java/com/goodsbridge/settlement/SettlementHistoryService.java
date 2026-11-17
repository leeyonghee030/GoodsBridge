package com.goodsbridge.settlement;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementHistoryService {
    private final SettlementHistoryRepository settlementHistoryRepository;
    private final ShopRepository shopRepository;

    private static final LocalDateTime MIN_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_TIME = LocalDateTime.of(2999, 1, 1, 0, 0);

    // 가게 정산 내역 조회 (유형 / 기간 조건은 선택, 기간은 from·to 날짜를 모두 포함한다)
    public Page<SettlementHistoryResponse.ListDto> findByShop(Long shopId, Long userId,
                                                              SettlementHistoryType type, LocalDate from, LocalDate to,
                                                              Pageable pageable) {
        validateAccess(shopId, userId, from, to);
        return settlementHistoryRepository
            .findPageByShop(shopId, type, startOf(from), endOf(to), pageable)
            .map(SettlementHistoryResponse.ListDto::new);
    }

    // 가게 매출 요약: 총매출, 구독료 지출, 순매출과 유형별 합계 (DB 집계)
    public SettlementHistoryResponse.SummaryDto summarizeByShop(Long shopId, Long userId,
                                                                SettlementHistoryType type, LocalDate from, LocalDate to) {
        validateAccess(shopId, userId, from, to);
        LocalDateTime fromAt = startOf(from);
        LocalDateTime toAt = endOf(to);
        List<Object[]> rows = settlementHistoryRepository.summarizeByType(shopId, type, fromAt, toAt);
        // 유형을 하나로 좁혀 본 경우에는 구독료를 차감하지 않는다
        long expense = type == null ? settlementHistoryRepository.sumSubscriptionExpense(shopId, fromAt, toAt) : 0L;
        return SettlementHistoryResponse.SummaryDto.of(rows, expense);
    }

    // 본인 가게인지와 기간 값을 확인
    private void validateAccess(Long shopId, Long userId, LocalDate from, LocalDate to) {
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("가게를 찾을 수 없습니다."));
        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인 가게의 정산 내역만 조회할 수 있습니다.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    private LocalDateTime startOf(LocalDate from) {
        return from == null ? MIN_TIME : from.atStartOfDay();
    }

    private LocalDateTime endOf(LocalDate to) {
        return to == null ? MAX_TIME : to.plusDays(1).atStartOfDay();   // 종료일 하루 전체 포함
    }

    // 정산 기록. 같은 결제와 유형은 한 번만 기록한다.
    @Transactional
    public void calculate(Shop shop, Payment payment, Integer amount, SettlementHistoryType type) {
        if (shop == null || payment == null || type == null) {
            throw new BadRequestException("정산 생성에 필요한 값이 없습니다.");
        }
        if (amount == null || amount <= 0) {
            return;
        }
        if (settlementHistoryRepository.existsByPaymentAndType(payment, type)) {
            return;
        }
        try {
            settlementHistoryRepository.save(new SettlementHistory(shop, payment, amount, type));
        } catch (DataIntegrityViolationException e) {
            // exists 검사를 동시에 통과한 중복 요청 -> uk_settlement_payment_type 제약이 마지막 방어선
            throw new BadRequestException("이미 정산이 기록된 결제입니다.");
        }
    }
}
