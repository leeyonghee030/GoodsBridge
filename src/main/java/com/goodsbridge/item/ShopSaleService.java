package com.goodsbridge.item;

import com.goodsbridge.payment.Payment;
import com.goodsbridge.purchase.Purchase;
import com.goodsbridge.refund.Refund;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 가게 사장님 판매 상품 관리 / 구매자 구매 내역 화면용 조회 (purchase/refund 도메인 코드는 건드리지 않고 읽기만 한다)
// 주의: 전액 환불이 승인되면 구매 기록이 삭제되므로 그 건은 여기 나오지 않는다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopSaleService {

    private final ShopRepository shopRepository;
    private final EntityManager em;

    public List<ShopSaleResponse> findMySales(Long userId) {
        List<Long> shopIds = shopRepository.findAllByOwnerUserIdAndDeletedFalse(userId, PageRequest.of(0, 20))
            .map(Shop::getId).getContent();
        if (shopIds.isEmpty()) return List.of();

        List<Purchase> purchases = em.createQuery("""
                select p from Purchase p
                join fetch p.item i join fetch i.category join fetch p.user
                where i.shopId in :shopIds
                order by p.id desc
                """, Purchase.class)
            .setParameter("shopIds", shopIds)
            .getResultList();
        return withPaymentsAndRefunds(purchases);
    }

    // 구매자: 내가 산 상품 (배송/확정/환불 상태 포함)
    public List<ShopSaleResponse> findMyPurchases(Long userId) {
        List<Purchase> purchases = em.createQuery("""
                select p from Purchase p
                join fetch p.item i join fetch i.category join fetch p.user u
                where u.id = :userId
                order by p.id desc
                """, Purchase.class)
            .setParameter("userId", userId)
            .getResultList();
        return withPaymentsAndRefunds(purchases);
    }

    // 구매 건마다 결제(환불 가능 금액)와 환불 내역을 붙여 응답으로 만든다
    private List<ShopSaleResponse> withPaymentsAndRefunds(List<Purchase> purchases) {
        if (purchases.isEmpty()) return List.of();

        List<String> paymentIds = purchases.stream().map(Purchase::getPaymentId).toList();
        Map<String, Payment> payments = em.createQuery(
                "select pay from Payment pay where pay.paymentId in :ids", Payment.class)
            .setParameter("ids", paymentIds)
            .getResultStream()
            .collect(Collectors.toMap(Payment::getPaymentId, Function.identity()));
        Map<String, List<Refund>> refunds = em.createQuery("""
                select r from Refund r join fetch r.payment pay
                where pay.paymentId in :ids
                order by r.id desc
                """, Refund.class)
            .setParameter("ids", paymentIds)
            .getResultStream()
            .collect(Collectors.groupingBy(r -> r.getPayment().getPaymentId()));

        return purchases.stream()
            .map(p -> new ShopSaleResponse(p, payments.get(p.getPaymentId()), refunds.getOrDefault(p.getPaymentId(), List.of())))
            .toList();
    }
}
