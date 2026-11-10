package com.goodsbridge.admin;

import com.goodsbridge.payment.Payment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

// 관리자 대시보드 전용 통계 조회 Repository.
public interface AdminRepository extends Repository<Payment, Long> {

    // ===== 월별 기본 통계 =====

    // 선택한 월의 유효 결제 건수
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

    // 선택한 월의 유효 결제 총액 (환불 차감 전)
    @Query("""
        select coalesce(sum(p.amount), 0)
        from Payment p
        where p.createdAt >= :startAt
          and p.createdAt < :endAt
          and p.status in (
              com.goodsbridge.payment.PaymentStatus.PAID,
              com.goodsbridge.payment.PaymentStatus.PARTIAL_CANCELLED,
              com.goodsbridge.payment.PaymentStatus.RELEASED
          )
        """)
    long sumPaymentAmount(@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    // 선택한 월의 실제 매출 (결제 금액 - 환불 금액)
    @Query("""
        select coalesce(sum(p.amount - p.refundedAmount), 0)
        from Payment p
        where p.createdAt >= :startAt
          and p.createdAt < :endAt
          and p.status in (
              com.goodsbridge.payment.PaymentStatus.PAID,
              com.goodsbridge.payment.PaymentStatus.PARTIAL_CANCELLED,
              com.goodsbridge.payment.PaymentStatus.RELEASED
          )
        """)
    long sumRevenue(@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    // ===== 운영 현황 (대시보드 확장) =====

    // 선택한 월에 결제된 건의 누적 환불액 (전액 환불 포함)
    @Query("""
        select coalesce(sum(p.refundedAmount), 0)
        from Payment p
        where p.createdAt >= :startAt
          and p.createdAt < :endAt
        """)
    long sumRefundedAmount(@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    // 선택한 월에 기록된 가게 정산 금액 합계
    @Query("""
        select coalesce(sum(s.amount), 0)
        from SettlementHistory s
        where s.createdAt >= :startAt
          and s.createdAt < :endAt
        """)
    long sumSettlementAmount(@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    // 결제 목적별 결제 금액. 행 = [purpose, sum(amount)]
    @Query("""
        select p.purpose, coalesce(sum(p.amount), 0)
        from Payment p
        where p.createdAt >= :startAt
          and p.createdAt < :endAt
          and p.status in (
              com.goodsbridge.payment.PaymentStatus.PAID,
              com.goodsbridge.payment.PaymentStatus.PARTIAL_CANCELLED,
              com.goodsbridge.payment.PaymentStatus.RELEASED
          )
        group by p.purpose
        """)
    List<Object[]> sumPaymentAmountByPurpose(@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    // ===== 처리 대기 현황 (월과 무관한 현재 값) =====

    // 관리자가 처리해야 하는 환불 요청 수 (월과 무관한 현재 값)
    @Query("""
        select count(r)
        from Refund r
        where r.status = com.goodsbridge.refund.Status.REQUESTED
        """)
    long countPendingRefunds();

    // 현재 진행 중인 경매 수
    @Query("""
        select count(a)
        from Auction a
        where a.status = com.goodsbridge.auction.Status.ACTIVE
        """)
    long countActiveAuctions();

    // 낙찰됐지만 아직 잔금을 내지 않은 경매
    @Query("""
        select count(a)
        from Auction a
        where a.status = com.goodsbridge.auction.Status.ENDED_SUCCESS
          and a.balancePaymentId is null
        """)
    long countUnpaidBalanceAuctions();

    // 결제는 끝났지만 아직 발송 전인 구매
    @Query("""
        select count(p)
        from Purchase p
        where p.status = com.goodsbridge.purchase.PurchaseStatus.PAID
          and p.deliveryStatus = com.goodsbridge.purchase.DeliveryStatus.PREPARING
        """)
    long countPendingShipments();
}
