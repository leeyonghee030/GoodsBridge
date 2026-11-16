package com.goodsbridge.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import com.goodsbridge.payment.Payment;

public interface SettlementHistoryRepository extends JpaRepository<SettlementHistory, Long> {

    @Query("""
        select s
        from SettlementHistory s
        where s.shop.id = :shopId
        order by s.id desc
        """)
    List<SettlementHistory> findByAllShopId(@Param("shopId") Long shopId);

    @Query("""
        select case when count(s) > 0 then true else false end
        from SettlementHistory s
        where s.payment = :payment
        and s.type = :type
        """)
    boolean existsByPaymentAndType(@Param("payment") Payment payment, @Param("type") SettlementHistoryType type);

    // 경매 낙찰 상품을 전액 환불하면 가게 정산(낙찰가 전액)도 취소한다
    void deleteByPaymentAndType(Payment payment, SettlementHistoryType type);
}
