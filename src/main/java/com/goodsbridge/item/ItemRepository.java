package com.goodsbridge.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findBySellerUserId(Long sellerUserId);

    @Query(value = """
               SELECT i FROM Item i
               JOIN FETCH i.category c
               LEFT JOIN Shop s ON i.shopId = s.id
               WHERE (:categoryId IS NULL OR c.id = :categoryId)
                 AND (:region IS NULL OR s.region = :region)
                 AND (:saleType IS NULL OR i.saleType = :saleType)
               """,
        countQuery = """
                    SELECT COUNT(i) FROM Item i
                    JOIN i.category c
                    LEFT JOIN Shop s ON i.shopId = s.id
                    WHERE (:categoryId IS NULL OR c.id = :categoryId)
                      AND (:region IS NULL OR s.region = :region)
                      AND (:saleType IS NULL OR i.saleType = :saleType)
                    """)
    Page<Item> searchItemsWithFilter(@Param("categoryId") Long categoryId,
                                     @Param("region") String region,
                                     @Param("saleType") SaleType saleType,
                                     Pageable pageable);

    // 특정 가게 ID 리스트에 속한 상품들의 상태를 일괄 변경 (구독 만료 시 CLOSED 처리용)
    // TODO - 스케줄러 등록해서 구독 만료된 가게들 일괄처리 시킬려고 만들어둠 일단 보류
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Item i SET i.status = :status WHERE i.shopId IN :shopIds
        """)
    void updateStatusByShopIds(@Param("shopIds") List<Long> shopIds, @Param("status") ItemStatus status);

    // 특정 가게의 특정 상태인 상품들을 다른 상태로 일괄 변경 (구독 갱신 시 CLOSED -> ON_SALE 복구용)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
UPDATE Item i SET i.status = :toStatus WHERE i.shopId = :shopId AND i.status = :fromStatus
""")
    void updateStatusByShopIdAndStatus(@Param("shopId") Long shopId,
                                       @Param("fromStatus") ItemStatus fromStatus,
                                       @Param("toStatus") ItemStatus toStatus);
}
