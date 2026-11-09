package com.goodsbridge.auction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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

    // 입찰자의 최초 입찰 1건 조회 (보증금 결제 ID 재사용용)
    // 파생 쿼리의 First는 LIMIT 1을 보장한다. 같은 유저의 입찰이 여러 건이어도 예외가 나지 않는다.
    Optional<Bid> findFirstByAuctionIdAndUserIdOrderByCreatedAtAscIdAsc(Long auctionId, Long userId);

    // 내가 참여한 경매 ID 목록
    @Query(value = """
        select b.auction.id from Bid b
        where b.user.id = :userId
        group by b.auction.id
        order by max(b.createdAt) desc
        """,
        countQuery = "select count(distinct b.auction.id) from Bid b where b.user.id = :userId")
    Page<Long> findParticipatedAuctionIds(@Param("userId") Long userId, Pageable pageable);

    // 낙찰자를 뺀 입찰자들의 보증금 결제 id (한 유저가 여러 번 입찰해도 보증금은 1건)
    @Query("""
        select distinct b.depositPaymentId from Bid b
        where b.auction.id = :auctionId and b.user.id <> :winnerId
        """)
    List<String> findLoserDepositPaymentIds(@Param("auctionId") Long auctionId, @Param("winnerId") Long winnerId);
}
