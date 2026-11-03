package com.goodsbridge.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;

public interface ItemJpaRepository extends JpaRepository<Item, Long> {
    // 내 상품 목록 조회
    List<Item> findBySellerUserId(Long SellerUserId);

    // category, region, saleType 동적 검색 및 페이징 처리 쿼리
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
}
