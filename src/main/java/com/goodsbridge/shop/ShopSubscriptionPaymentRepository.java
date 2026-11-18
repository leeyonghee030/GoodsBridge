package com.goodsbridge.shop;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 가게 구독 연장 시 확인용
public interface ShopSubscriptionPaymentRepository extends JpaRepository<ShopSubscriptionPayment, Long> {
    @Query("""
        select case when count(s) > 0 then true else false end
        from ShopSubscriptionPayment s
        where s.paymentId = :paymentId
        """)
    boolean existsByPaymentId(@Param("paymentId") String paymentId);
}
