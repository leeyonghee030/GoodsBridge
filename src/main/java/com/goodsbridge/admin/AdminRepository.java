package com.goodsbridge.admin;

import com.goodsbridge.payment.Payment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.time.LocalDateTime;

public interface AdminRepository extends Repository<Payment, Long> {

    // ===== 처리 대기 현황 (월과 무관한 현재 값) =====

    // 환불 승인 대기
    @Query("""
        select count(r)
        from Refund r
        where r.status = com.goodsbridge.refund.Status.REQUESTED
        """)
    long countPendingRefunds();

    // 결제는 끝났지만 아직 발송 전인 구매 건
    @Query("""
        select count(p)
        from Purchase p
        where p.status = com.goodsbridge.purchase.PurchaseStatus.PAID
          and p.deliveryStatus = com.goodsbridge.purchase.DeliveryStatus.PREPARING
        """)
    long countPendingShipments();

    // 현재 진행 중인 경매 수
    @Query("""
        select count(a)
        from Auction a
        where a.status = com.goodsbridge.auction.Status.ACTIVE
        """)
    long countActiveAuctions();

    // 종료된 경매 수 (낙찰 성공, 낙찰 실패, 취소). 날짜 조건이 없는 누적 값이다.
    @Query("""
        select count(a)
        from Auction a
        where a.status in (
            com.goodsbridge.auction.Status.ENDED_SUCCESS,
            com.goodsbridge.auction.Status.ENDED_FAIL,
            com.goodsbridge.auction.Status.CANCELLED
        )
        """)
    long countEndedAuctions();

    // 낙찰됐지만 아직 잔금을 내지 않은 경매
    @Query("""
        select count(a)
        from Auction a
        where a.status = com.goodsbridge.auction.Status.ENDED_SUCCESS
          and a.balancePaymentId is null
        """)
    long countUnpaidBalanceAuctions();

    // ===== 월별 지표 (startAt 이상 ~ endAt 미만) =====

    // 월 가입자 수. User.createdAt이 Timestamp라서 파라미터도 Timestamp로 받는다.
    @Query("""
        select count(u)
        from User u
        where u.createdAt >= :startAt
          and u.createdAt < :endAt
        """)
    long countSignups(@Param("startAt") Timestamp startAt, @Param("endAt") Timestamp endAt);

    // 월 거래 건수
    @Query("""
        select count(p)
        from Payment p
        where p.createdAt >= :startAt
          and p.createdAt < :endAt
          and p.status in (
              com.goodsbridge.payment.PaymentStatus.PAID,
              com.goodsbridge.payment.PaymentStatus.PARTIAL_CANCELLED,
              com.goodsbridge.payment.PaymentStatus.RELEASED
          )
        """)
    long countPayments(@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    // 월 환불 금액 (그 달에 결제된 건의 누적 환불액)
    @Query("""
        select coalesce(sum(p.refundedAmount), 0)
        from Payment p
        where p.createdAt >= :startAt
          and p.createdAt < :endAt
        """)
    long sumRefundedAmount(@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    // 월 정산 금액 (가게에 기록된 정산 합계)
    @Query("""
        select coalesce(sum(s.amount), 0)
        from SettlementHistory s
        where s.createdAt >= :startAt
          and s.createdAt < :endAt
        """)
    long sumSettlementAmount(@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    // 플랫폼 월 매출 = 가게 구독료만 (결제액 - 환불액)
    @Query("""
        select coalesce(sum(p.amount - p.refundedAmount), 0)
        from Payment p
        where p.createdAt >= :startAt
          and p.createdAt < :endAt
          and p.purpose = com.goodsbridge.payment.PaymentPurpose.SHOP_SUBSCRIPTION
          and p.status in (
              com.goodsbridge.payment.PaymentStatus.PAID,
              com.goodsbridge.payment.PaymentStatus.PARTIAL_CANCELLED,
              com.goodsbridge.payment.PaymentStatus.RELEASED
          )
        """)
    long sumPlatformRevenue(@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    // 가게 월 매출
    // 일반 판매 대금 + 교환 수수료 + 노쇼 몰수금 + 경매 낙찰가(전액) + 이벤트 참가비.
    @Query("""
        select coalesce(sum(s.amount), 0)
        from SettlementHistory s
        where s.createdAt >= :startAt
          and s.createdAt < :endAt
          and s.type in (
              com.goodsbridge.settlement.SettlementHistoryType.PURCHASE_FEE,
              com.goodsbridge.settlement.SettlementHistoryType.EXCHANGE_FEE,
              com.goodsbridge.settlement.SettlementHistoryType.NO_SHOW_FEE,
              com.goodsbridge.settlement.SettlementHistoryType.AUCTION_SALE,
              com.goodsbridge.settlement.SettlementHistoryType.EVENT_FEE
          )
        """)
    long sumShopRevenue(@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);
}
