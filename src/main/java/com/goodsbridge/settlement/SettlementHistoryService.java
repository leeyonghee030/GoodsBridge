package com.goodsbridge.settlement;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import lombok.RequiredArgsConstructor;
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

    // 가게 정산 내역 전체 조회
    public List<SettlementHistoryResponse.ListDto> findByShop(Long shopId, Long userId) {
        return findByShop(shopId, userId, null, null, null);
    }

    // 가게 정산 내역 조회 (유형 / 기간 조건은 선택, 기간은 from·to 날짜를 모두 포함한다)
    public List<SettlementHistoryResponse.ListDto> findByShop(Long shopId, Long userId,
                                                              SettlementHistoryType type, LocalDate from, LocalDate to) {
        return findFiltered(shopId, userId, type, from, to).stream()
            .map(SettlementHistoryResponse.ListDto::new)
            .toList();
    }

    // 가게 매출 요약: 정산 총액, 플랫폼 수수료, 실수령액 합계와 유형별 합계
    public SettlementHistoryResponse.SummaryDto summarizeByShop(Long shopId, Long userId,
                                                                SettlementHistoryType type, LocalDate from, LocalDate to) {
        return new SettlementHistoryResponse.SummaryDto(findFiltered(shopId, userId, type, from, to));
    }

    // 본인 가게인지 확인한 뒤 조건에 맞는 정산 엔티티를 최신순으로 조회
    private List<SettlementHistory> findFiltered(Long shopId, Long userId,
                                                 SettlementHistoryType type, LocalDate from, LocalDate to) {
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("가게를 찾을 수 없습니다."));
        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인 가게의 정산 내역만 조회할 수 있습니다.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("조회 시작일은 종료일보다 늦을 수 없습니다.");
        }
        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();   // 종료일 하루 전체 포함
        return settlementHistoryRepository.findByAllShopId(shopId).stream()
            .filter(history -> type == null || history.getType() == type)
            .filter(history -> fromTime == null || (history.getCreatedAt() != null && !history.getCreatedAt().isBefore(fromTime)))
            .filter(history -> toTime == null || (history.getCreatedAt() != null && history.getCreatedAt().isBefore(toTime)))
            .toList();
    }

    // 수수료 없이 정산 기록 (기존 호출부 호환)
    @Transactional
    public void calculate(Shop shop, Payment payment, Integer amount, SettlementHistoryType type) {
        calculate(shop, payment, amount, 0, type);
    }

    // 총액(amount)과 플랫폼 수수료(platformFee)를 함께 기록. 같은 결제+타입은 한 번만 기록한다.
    @Transactional
    public void calculate(Shop shop, Payment payment, Integer amount, Integer platformFee, SettlementHistoryType type) {
        if (shop == null || payment == null || type == null) {
            throw new BadRequestException("정산 생성에 필요한 값이 없습니다.");
        }
        if (amount == null || amount <= 0) {
            return;
        }
        if (settlementHistoryRepository.existsByPaymentAndType(payment, type)) {
            return;
        }
        if (platformFee == null || platformFee < 0 || platformFee > amount) {
            throw new BadRequestException("플랫폼 수수료가 올바르지 않습니다.");
        }
        settlementHistoryRepository.save(new SettlementHistory(shop, payment, amount, platformFee, type));
    }
}
