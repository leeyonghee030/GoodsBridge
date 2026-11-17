package com.goodsbridge.event;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShopEventRepository extends JpaRepository<ShopEvent, Long> {

    //목록보기
    @Query("""
    select s From ShopEvent s
    join fetch s.shop
    join fetch s.item
    where (:shopId is null or s.shop.id = :shopId) and
    (:status is null or s.status = :status)

""")
    List<ShopEvent> getShopEventAll(@Param("shopId") Long shopId,@Param("status") ShopEventStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShopEvent s where s.id = :id")
    Optional<ShopEvent> findByIdForUpdate(@Param("id") Long id);


    // 마감시간이 지났는데 아직 정산 안 된 이벤트 (정원이 차서 먼저 CLOSED 된 이벤트도 포함)
    @Query("""
    select s from ShopEvent s
    join fetch s.shop
    where s.status <> com.goodsbridge.event.ShopEventStatus.CANCELLED
          and s.settled = false and s.deadline < :now
""")
    List<ShopEvent> findSettleTargets(@Param("now") LocalDateTime now);

    // 가게 삭제 차단용: 취소되지 않았고 아직 정산이 끝나지 않은 이벤트가 있는지
    // (마감시간이 지났어도 정산 전이면 참가자 처리가 남아 있으므로 진행 중으로 본다)
    @Query("""
    select count(s) > 0 from ShopEvent s
    where s.shop.id = :shopId
      and s.status <> com.goodsbridge.event.ShopEventStatus.CANCELLED
      and s.settled = false
""")
    boolean existsOngoingByShopId(@Param("shopId") Long shopId);

}
