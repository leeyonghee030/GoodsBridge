package com.goodsbridge.admin;

import com.goodsbridge.payment.Payment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AdminRepository extends Repository<Payment, Long> {

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
    long countPayments(
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt
    );

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
    long sumPaymentAmount(
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt
    );

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
    long sumRevenue(
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt
    );
}
