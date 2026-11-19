package com.goodsbridge.purchase;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    // 구매 여부 확인(true, false로 응답 필요할 때)
    @Query("""
        select case when count(p) > 0 then true else false end
        from Purchase p
        where p.user.id = :userId and p.item.id = :itemId
          and p.status <> com.goodsbridge.purchase.PurchaseStatus.REFUNDED
        """)
    boolean existsByUserIdAndItemId(@Param("userId") Long userId, @Param("itemId") Long itemId);

    // 리뷰를 쓸 수 있는 구매: 내가 구매확정했고 아직 리뷰가 달리지 않은 구매 (오래된 순)
    @Query("""
        select p from Purchase p
        where p.user.id = :userId
          and p.item.id = :itemId
          and p.status = com.goodsbridge.purchase.PurchaseStatus.CONFIRMED
          and not exists (select b.id from Board b where b.purchaseId = p.id)
        order by p.id asc
        """)
    List<Purchase> findReviewablePurchases(@Param("userId") Long userId, @Param("itemId") Long itemId);

    // 누가 샀든 이미 판매된 상품인지 확인 (결제 검증 전에 먼저 차단)
    @Query("""
        select case when count(p) > 0 then true else false end
        from Purchase p
        where p.item.id = :itemId
          and p.status <> com.goodsbridge.purchase.PurchaseStatus.REFUNDED
        """)
    boolean existsByItemId(@Param("itemId") Long itemId);

    // 구매 내역 조회
    @Query("""
        select p
        from Purchase p
        join fetch p.user
        join fetch p.item
        where p.user.id = :userId
        order by p.id desc""")
    List<Purchase> findAllPurchaseByUserId(@Param("userId") Long userId);

    // 전체 상품 조회
    @Query("""
            select p from Purchase p join fetch p.item i
            where i.shopId = :shopId
              and p.status <> com.goodsbridge.purchase.PurchaseStatus.REFUNDED
              and (:ds is null or p.deliveryStatus = :ds)
            order by p.id desc
        """)
    List<Purchase> findAllByShop(@Param("shopId") Long shopId, @Param("ds") DeliveryStatus ds);

    // 결제 ID 조회
    @Query("""
        select p
        from Purchase p
        where p.paymentId = :paymentId
        """)
    Optional<Purchase> findByPaymentId(@Param("paymentId") String paymentId);

    // 가게 삭제 차단용: 이 가게 상품의 구매 중 아직 배송이 끝나지 않은(환불되지 않은) 건이 있는지
    @Query("""
        select count(p) > 0 from Purchase p
        where p.item.shopId = :shopId
          and p.status <> com.goodsbridge.purchase.PurchaseStatus.REFUNDED
          and p.deliveryStatus in (com.goodsbridge.purchase.DeliveryStatus.PREPARING,
                                   com.goodsbridge.purchase.DeliveryStatus.SHIPPED)
        """)
    boolean existsUndeliveredByShopId(@Param("shopId") Long shopId);

    // 회원 탈퇴 차단용: 아직 배송이 끝나지 않은(환불되지 않은) 구매가 있는지
    @Query("""
        select count(p) > 0 from Purchase p
        where p.user.id = :userId
          and p.status <> com.goodsbridge.purchase.PurchaseStatus.REFUNDED
          and p.deliveryStatus in (com.goodsbridge.purchase.DeliveryStatus.PREPARING,
                                   com.goodsbridge.purchase.DeliveryStatus.SHIPPED)
        """)
    boolean existsUndeliveredByUserId(@Param("userId") Long userId);

    // 배송 상태 변경용 잠금 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Purchase p where p.id = :id")
    Optional<Purchase> findByIdLock(@Param("id") Long id);
}
