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

    // 가게의 경매 목록 (상태는 선택)
    @Query("""
            select a from Auction a
            where a.shop.id = :shopId
              and (:status is null or a.status = :status)
        """)
    Page<Auction> findAllByShop(@Param("shopId") Long shopId, @Param("status") Status status, Pageable pageable);

    // 경매 마감 시간이 지난 경매 조회
    @Query("""
        select a
        from Auction a
        where a.status = :status
          and a.deadline <= :deadline
        """)
    Page<Auction> findByStatusAndDeadlineBefore(@Param("status") Status status,
                                                @Param("deadline") LocalDateTime deadline,
                                                Pageable pageable);

    // 마감 1시간 이내이며 임박 알림을 아직 보내지 않은 진행 중 경매
    @Query("""
        select a.id from Auction a
        where a.status = :status
          and a.endingSoonNotified = false
          and a.deadline > :now
          and a.deadline <= :limit
        order by a.deadline asc
        """)
    List<Long> findIdsEndingSoon(@Param("status") Status status,
                                 @Param("now") LocalDateTime now,
                                 @Param("limit") LocalDateTime limit);

    // 잔금 결제 기한이 곧 끝나는(아직 안 낸) 낙찰 경매. 기한 임박 알림을 한 번만 보낸다
    @Query("""
        select a.id from Auction a
        where a.status = :status
          and a.balancePaymentId is null
          and a.balanceReminderNotified = false
          and a.balanceDueAt > :now
          and a.balanceDueAt <= :limit
        order by a.balanceDueAt asc
        """)
    List<Long> findIdsBalanceDueSoon(@Param("status") Status status,
                                     @Param("now") LocalDateTime now,
                                     @Param("limit") LocalDateTime limit);

    // 동시 마감 막기
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findByIdLock(@Param("id") Long id);

    // 낙찰 결제 ID로 해당 경매 조회
    @Query("""
        select a
        from Auction a
        where a.balancePaymentId = :balancePaymentId
        """)
    Optional<Auction> findByBalancePaymentId(@Param("balancePaymentId") String balancePaymentId);

    // 낙찰 결제 ID로 해당 경매 조회
    @Query("""
        select a
        from Auction a
        where a.winningPaymentId = :winningPaymentId
        """)
    Optional<Auction> findByWinningPaymentId(@Param("winningPaymentId") String winningPaymentId);

    // 같은 상품으로 진행 중인 경매가 이미 있는지 확인
    @Query("""
        select case when count(a) > 0 then true else false end
        from Auction a
        where a.item.id = :itemId and a.status = :status
        """)
    boolean existsByItemIdAndStatus(@Param("itemId") Long itemId, @Param("status") Status status);

    // 상품의 진행 중인 경매 id (최신순). 경매 전용 상품의 상세 페이지에서 경매로 안내할 때 쓴다
    @Query("""
        select a.id from Auction a
        where a.item.id = :itemId and a.status = com.goodsbridge.auction.Status.ACTIVE
        order by a.id desc
        """)
    List<Long> findActiveAuctionIdsByItemId(@Param("itemId") Long itemId);

    // 상품이 지금 경매에 쓰이는 중인지: 진행 중인 경매가 있거나, 낙찰됐지만 낙찰자가 아직 잔금을 결제하지 않은 경매가 있다
    @Query("""
        select count(a) > 0 from Auction a
        where a.item.id = :itemId
          and (a.status = com.goodsbridge.auction.Status.ACTIVE
               or (a.status = com.goodsbridge.auction.Status.ENDED_SUCCESS and a.balancePaymentId is null))
        """)
    boolean existsInUseByItemId(@Param("itemId") Long itemId);

    // 가게 삭제 차단용: 진행 중이거나, 낙찰됐지만 잔금 미결제인 경매가 이 가게에 있는지
    @Query("""
        select count(a) > 0 from Auction a
        where a.shop.id = :shopId
          and (a.status = com.goodsbridge.auction.Status.ACTIVE
               or (a.status = com.goodsbridge.auction.Status.ENDED_SUCCESS and a.balancePaymentId is null))
        """)
    boolean existsOngoingByShopId(@Param("shopId") Long shopId);

    // 회원 탈퇴 차단용: 내가 입찰한 진행 중 경매가 있거나, 내가 낙찰자인데 잔금을 결제하지 않은 경매가 있는지
    @Query("""
        select count(a) > 0 from Auction a
        where (a.status = com.goodsbridge.auction.Status.ACTIVE
               and exists (select 1 from Bid b where b.auction = a and b.user.id = :userId))
           or (a.status = com.goodsbridge.auction.Status.ENDED_SUCCESS
               and a.balancePaymentId is null and a.currentWinner.id = :userId)
        """)
    boolean existsOngoingByBidderId(@Param("userId") Long userId);

    // 낙찰됐지만 낙찰자가 아직 잔금을 결제하지 않은 경매가 있는 상품인지 (이런 상품은 새 경매에 올릴 수 없다)
    @Query("""
        select count(a) > 0 from Auction a
        where a.item.id = :itemId
          and a.status = com.goodsbridge.auction.Status.ENDED_SUCCESS
          and a.balancePaymentId is null
        """)
    boolean existsUnpaidWinnerByItemId(@Param("itemId") Long itemId);

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
