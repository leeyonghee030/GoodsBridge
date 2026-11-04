package com.goodsbridge.auction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.util.List;

public interface AuctionRepository extends JpaRepository<Auction, Long> {
    // 경매 상태 조회
    @Query("""
            select a from Auction a
            where a.status = :status
            order by a.id desc
        """)
    Page<Auction> findAllByStatus(@Param("status") Status status, Pageable pageable);

    // 경매 마감 시간이 지난 경매 조회
    List<Auction> findByStatusAndDeadlineBefore(Status status, Timestamp deadline);
}
