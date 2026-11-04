package com.goodsbridge.purchase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    // 구매 내역 조회(해당 게시글 PK 기준)
    @Query("select p from Purchase p where p.user.id = :userId")
    Optional<Purchase> findByUserId(@Param("userId") Long userId);

    // 구매 여부 확인(true, false로 응답 필요할 때)
    @Query("""
        select case when count(p) > 0 then true else false end
        from Purchase p
        where p.user.id = :userId and p.item.id = :itemId
        """)
    boolean existsByUserIdAndItemId(@Param("userId") Long userId, @Param("itemId") Long itemId);

    // 구매 내역 조회
    @Query("select p from Purchase p join fetch p.user where p.user.id = :userId order by p.id desc")
    List<Purchase> findAllPurchaseByUserId(@Param("userId") Long userId);

    // 전체 상품 조회
    @Query("""
            select p from Purchase p join fetch p.item i
            where i.shop.id = :shopId
              and (:ds is null or p.deliveryStatus = :ds)
            order by p.id desc
        """)
    List<Purchase> findAllByShop(@Param("shopId") Long shopId, @Param("ds") DeliveryStatus ds);
}
