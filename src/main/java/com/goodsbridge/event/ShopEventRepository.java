package com.goodsbridge.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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



}
