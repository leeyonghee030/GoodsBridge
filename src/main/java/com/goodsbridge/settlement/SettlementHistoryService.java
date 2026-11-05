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

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementHistoryService {
    private final SettlementHistoryRepository settlementHistoryRepository;
    private final ShopRepository shopRepository;

    public List<SettlementHistoryResponse.ListDto> findByShop(Long shopId, Long userId) {
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("가게를 찾을 수 없습니다."));
        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인 가게의 정산 내역만 조회할 수 있습니다.");
        }
        return settlementHistoryRepository.findByAllShopId(shopId)
            .stream()
            .map(SettlementHistoryResponse.ListDto::new)
            .toList();
    }

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
        settlementHistoryRepository.save(new SettlementHistory(shop, payment, amount, type));
    }
}
