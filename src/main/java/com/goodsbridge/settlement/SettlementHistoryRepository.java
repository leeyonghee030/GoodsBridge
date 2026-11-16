package com.goodsbridge.settlement;

import com.goodsbridge.payment.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SettlementHistoryRepository extends JpaRepository<SettlementHistory, Long> {

    // 가게 정산 목록
    @Query(value = """
        select s
        from SettlementHistory s
        join fetch s.payment
        where s.shop.id = :shopId
          and (:type is null or s.type = :type)
          and s.createdAt >= :fromAt
          and s.createdAt < :toAt
        """,
        countQuery = """
        select count(s)
        from SettlementHistory s
        where s.shop.id = :shopId
          and (:type is null or s.type = :type)
          and s.createdAt >= :fromAt
          and s.createdAt < :toAt
        """)
    Page<SettlementHistory> findPageByShop(@Param("shopId") Long shopId,
                                           @Param("type") SettlementHistoryType type,
                                           @Param("fromAt") LocalDateTime fromAt,
                                           @Param("toAt") LocalDateTime toAt,
                                           Pageable pageable);

    // 유형별 건수와 합계
    @Query("""
        select s.type, count(s), coalesce(sum(s.amount), 0)
        from SettlementHistory s
        where s.shop.id = :shopId
          and (:type is null or s.type = :type)
          and s.createdAt >= :fromAt
          and s.createdAt < :toAt
        group by s.type
        """)
    List<Object[]> summarizeByType(@Param("shopId") Long shopId,
                                   @Param("type") SettlementHistoryType type,
                                   @Param("fromAt") LocalDateTime fromAt,
                                   @Param("toAt") LocalDateTime toAt);

    // 가게가 낸 구독료
    @Query("""
        select coalesce(sum(p.amount - p.refundedAmount), 0)
        from Payment p, ShopSubscription ss
        where ss.paymentId = p.paymentId
          and ss.shopId = :shopId
          and p.purpose = com.goodsbridge.payment.PaymentPurpose.SHOP_SUBSCRIPTION
          and p.createdAt >= :fromAt
          and p.createdAt < :toAt
        """)
    long sumSubscriptionExpense(@Param("shopId") Long shopId,
                                @Param("fromAt") LocalDateTime fromAt,
                                @Param("toAt") LocalDateTime toAt);

    @Query("""
        select case when count(s) > 0 then true else false end
        from SettlementHistory s
        where s.payment = :payment
        and s.type = :type
        """)
    boolean existsByPaymentAndType(@Param("payment") Payment payment, @Param("type") SettlementHistoryType type);
}
