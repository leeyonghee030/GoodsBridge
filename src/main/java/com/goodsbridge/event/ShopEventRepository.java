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


    @Query("""
    select s from ShopEvent s
    where s.status = :status and s.deadline < :deadline
""")
    List<ShopEvent> findByStatusAndDeadlineBefore(@Param("status") ShopEventStatus status
        , @Param("deadline") LocalDateTime deadline);

}
