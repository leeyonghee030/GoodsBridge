package com.goodsbridge.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    // paymentId 조회용
    @Query("""
            select case when count(p) > 0 then true else false end
            from Payment p
            where p.paymentId = :paymentId
        """)
    boolean existsByPaymentId(@Param("paymentId") String paymentId);

    // 내역 목록 조회용
    @Query("""
            select p from Payment p
            where p.user.id = :userId
            order by p.id desc
        """)
    List<Payment> findAllByUserIdOrderByIdDesc(@Param("userId") Long userId);
}
