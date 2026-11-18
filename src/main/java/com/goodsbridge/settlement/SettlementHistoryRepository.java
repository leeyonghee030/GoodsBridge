package com.goodsbridge.settlement;

import com.goodsbridge.payment.Payment;
import com.goodsbridge.shop.ShopSubscriptionPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
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

    // 경매 관리 화면용 정산 목록. 기존 화면 계약이 List이므로 경매 유형만 조회한다.
    @Query("""
        select s
        from SettlementHistory s
        join fetch s.payment
        where s.shop.id = :shopId
          and s.type in (
              com.goodsbridge.settlement.SettlementHistoryType.AUCTION_SALE,
              com.goodsbridge.settlement.SettlementHistoryType.AUCTION_DEPOSIT_FORFEIT
          )
        order by s.id desc
        """)
    List<SettlementHistory> findAuctionSettlementsByShop(@Param("shopId") Long shopId);

    // 결제, 정산 삭제
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        delete from SettlementHistory s
        where s.payment = :payment
          and s.type = :type
        """)
    void deleteByPaymentAndType(@Param("payment") Payment payment,
                                @Param("type") SettlementHistoryType type);

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
        select coalesce(sum(p.amount - coalesce(p.refundedAmount, 0)), 0)
        from Payment p, ShopSubscriptionPayment ssp
        where ssp.paymentId = p.paymentId
          and ssp.shopId = :shopId
          and p.purpose = com.goodsbridge.payment.PaymentPurpose.SHOP_SUBSCRIPTION
          and p.createdAt >= :fromAt
          and p.createdAt < :toAt
        """)
    long sumSubscriptionExpense(@Param("shopId") Long shopId,
                                @Param("fromAt") LocalDateTime fromAt,
                                @Param("toAt") LocalDateTime toAt);

    // 해당 결제의 정산 유형 기록 존재 확인
    @Query("""
        select case when count(s) > 0 then true else false end
        from SettlementHistory s
        where s.payment = :payment
        and s.type = :type
        """)
    boolean existsByPaymentAndType(@Param("payment") Payment payment, @Param("type") SettlementHistoryType type);
}
