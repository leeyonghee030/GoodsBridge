package com.goodsbridge.refund;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    @Query("""
        select r
        from Refund r
        where r.user.id = :userId
        order by r.id desc
        """)
    List<Refund> findAllByUserIdOrderByIdDesc(@Param("userId") Long userId);

    // 관리자 - 승인할 환불 요청 1건 조회
    @Query("""
        select r from Refund r
        join fetch r.user
        join fetch r.payment
        where r.id  = :id
        """)
    Optional<Refund> findByIdJoinUserAndPayment(@Param("id") Long id);

    @Query("""
        select r from Refund r
        order by r.id desc
        """)
    List<Refund> findAllByOrderByIdDesc();

    // 중복 환불 요청 방지
    @Query("""
            select count(r) > 0
            from Refund r
            where r.payment.paymentId = :paymentId
              and r.status = com.goodsbridge.refund.Status.REQUESTED
        """)
    boolean existsPendingByPaymentId(@Param("paymentId") String paymentId);

    /**
     * LOCK : 여러 트랜잭션이 동시에 같은 데이터를 수정하거나 조회할 때 발생할 수 있는 동시성 문제 방기
     * 비관적 락 : 다른 트랜잭션이 동시에 수정하지 못하도록 DB에서 잠금
     * 낙관적 락 : 실제 수정 시점에 데이터 변경 여부를 확인하여 충돌 감지
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from Refund r
            join fetch r.user
            join fetch r.payment
            where r.id = :id
        """)
    Optional<Refund> findByIdLock(@Param("id") Long id);
}
