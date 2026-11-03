package com.goodsbridge.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemJpaRepository extends JpaRepository<Item, Long> {

    List<Item> findBySellerUserId(Long sellerUserId);

    @Query(value = """
               SELECT i FROM Item i
               JOIN FETCH i.category c
               WHERE (:categoryId IS NULL OR c.id = :categoryId)
                 AND (:saleType IS NULL OR i.saleType = :saleType)
               """,
        countQuery = """
                    SELECT COUNT(i) FROM Item i
                    JOIN i.category c
                    WHERE (:categoryId IS NULL OR c.id = :categoryId)
                      AND (:saleType IS NULL OR i.saleType = :saleType)
                    """)
    Page<Item> searchItemsWithFilter(@Param("categoryId") Long categoryId,
//                                     @Param("region") String region, // TODO - 파라미터는 유지 (추후 Shop 연동 시 사용) 추후 쿼리에 AND (:region IS NULL OR s.region = :region) 추가
                                     @Param("saleType") SaleType saleType,
                                     Pageable pageable);
}
