package com.goodsbridge.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PaymentCancelFailureRepository extends JpaRepository<PaymentCancelFailure, Long> {

    @Query("""
        select case when count(f) > 0 then true else false end
        from PaymentCancelFailure f
        where f.paymentId = :paymentId
    """)
    boolean existsByPaymentId(String paymentId);

    @Query("""
        select f from PaymentCancelFailure f
        where f.status = :status
        order by f.id asc
    """)
    List<PaymentCancelFailure> findTop20ByStatusOrderByIdAsc(PaymentCancelFailure.Status status);
}
