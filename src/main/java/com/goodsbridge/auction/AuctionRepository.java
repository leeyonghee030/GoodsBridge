package com.goodsbridge.auction;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a from Auction a
        where a.id = :id
        """)
    Optional<Auction> findByIdLock(@Param("id") Long id);
}
