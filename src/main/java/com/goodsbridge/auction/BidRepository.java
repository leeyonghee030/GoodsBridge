package com.goodsbridge.auction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BidRepository extends JpaRepository<Bid, Long> {
    // 최초 입찰 여부 판단
    @Query("""
        select case when count(b) > 0 then true else false end
        from Bid b
        where b.auction.id = :auctionId and b.user.id = :userId
        """)
    boolean existsByAuctionIdAndUserId(@Param("auctionId") Long auctionId, @Param("userId") Long userId);

    // 입찰 내역 조회
    @Query(value = """
        select b from Bid b
        join fetch b.user
        where b.auction.id = :auctionId
        order by b.bidAmount desc
        """,
        countQuery = "select count(b) from Bid b where b.auction.id = :auctionId")
    Page<Bid> findBidsByAuctionId(@Param("auctionId") Long auctionId, Pageable pageable);

    // 내가 참여한 경매 ID 목록
    @Query(value = """
        select b.auction.id from Bid b
        where b.user.id = :userId
        group by b.auction.id
        order by max(b.createdAt) desc
        """,
        countQuery = "select count(distinct b.auction.id) from Bid b where b.user.id = :userId")
    Page<Long> findParticipatedAuctionIds(@Param("userId") Long userId, Pageable pageable);
}
