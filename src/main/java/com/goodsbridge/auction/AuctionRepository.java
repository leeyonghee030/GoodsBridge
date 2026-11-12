package com.goodsbridge.auction;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {
    // 경매 상태 조회
    @Query("""
            select a from Auction a
            where a.status = :status
            order by a.id desc
        """)
    Page<Auction> findAllByStatus(@Param("status") Status status, Pageable pageable);

    // 경매 마감 시간이 지난 경매 조회
    @Query("""
        select a
        from Auction a
        where a.status = :status
          and a.deadline < :deadline
        """)
    Page<Auction> findByStatusAndDeadlineBefore(@Param("status") Status status,
                                                @Param("deadline") LocalDateTime deadline,
                                                Pageable pageable);

    // 동시 마감 막기
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findByIdLock(@Param("id") Long id);

    // 같은 상품으로 진행 중인 경매가 이미 있는지 확인
    @Query("""
        select case when count(a) > 0 then true else false end
        from Auction a
        where a.item.id = :itemId and a.status = :status
        """)
    boolean existsByItemIdAndStatus(@Param("itemId") Long itemId, @Param("status") Status status);

    // 상품이 주어진 상태들 중 하나의 경매에 걸려 있는지 확인 (일반 구매 차단용)
    @Query("""
        select case when count(a) > 0 then true else false end
        from Auction a
        where a.item.id = :itemId and a.status in :statuses
        """)
    boolean existsByItemIdAndStatusIn(@Param("itemId") Long itemId, @Param("statuses") Collection<Status> statuses);

    // 현재 1위(낙찰 후보) 유저 id - 트랜잭션 밖에서 지연로딩 없이 쓰려고 스칼라로 조회
    @Query("select a.currentWinner.id from Auction a where a.id = :id")
    Long findWinnerUserId(@Param("id") Long id);

    // 패찰자 보증금 환불이 아직 안 끝난 경매 (입찰자가 있었던 종료 경매)
    @Query("""
        select a.id from Auction a
        where a.status in :statuses
          and a.losersRefunded = false
          and a.currentWinner is not null
        order by a.id asc
        """)
    List<Long> findIdsPendingLoserRefund(@Param("statuses") Collection<Status> statuses);

    // 취소된 경매 중 입찰이 있었고 보증금 환불이 아직 안 끝난 경매
    @Query("""
        select a.id from Auction a
        where a.status = :status
          and a.losersRefunded = false
          and a.currentWinner is not null
        order by a.id asc
        """)
    List<Long> findIdsPendingCancelRefund(@Param("status") Status status);

    // 잔금 결제 기한이 지났는데 아직 미결제인 낙찰 경매
    @Query("""
        select a.id from Auction a
        where a.status = :status
          and a.balancePaymentId is null
          and a.balanceDueAt is not null
          and a.balanceDueAt < :now
        order by a.id asc
        """)
    List<Long> findIdsUnpaidOverdue(@Param("status") Status status, @Param("now") LocalDateTime now);
}
