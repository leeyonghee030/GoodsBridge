package com.goodsbridge.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {
    // 특정 판매자(사용자) ID에 해당하는 모든 상품 목록을 조회
    List<Item> findBySellerUserId(Long sellerUserId);

    // 특정 카테고리에 속한 상품 개수 조회
    Long countByCategoryId(Long categoryId);

    //조건(카테고리, 지역, 판매 유형)에 맞는 상품을 페이징 처리하여 검색
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

    // 여러 상점 ID에 속한 모든 상품들의 상태(Status)를 일괄 변경
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Item i SET i.status = :status WHERE i.shopId IN :shopIds
        """)
    void updateStatusByShopIds(@Param("shopIds") List<Long> shopIds, @Param("status") ItemStatus status);

    // 특정 상점에 속하고, 특정 상태를 가진 상품들의 상태를 다른 상태로 일괄 변경
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    UPDATE Item i SET i.status = :toStatus WHERE i.shopId = :shopId AND i.status = :fromStatus
    """)
    void updateStatusByShopIdAndStatus(@Param("shopId") Long shopId,
                                       @Param("fromStatus") ItemStatus fromStatus,
                                       @Param("toStatus") ItemStatus toStatus);
}
