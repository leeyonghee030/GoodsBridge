package com.goodsbridge.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {
    // 삭제되지 않은 특정 판매자의 상품 목록 조회
    @Query("SELECT i FROM Item i WHERE i.sellerUserId = :sellerUserId AND i.deleted = false")
    List<Item> findBySellerUserIdAndDeletedFalse(@Param("sellerUserId") Long sellerUserId);

    // 특정 카테고리에 속한 상품 개수 조회
    Long countByCategoryId(Long categoryId);


    // 삭제되지 않은 특정 카테고리에 속한 상품 개수 조회
    Long countByCategoryIdAndDeletedFalse(Long categoryId);

    // 단건 조회 시 삭제되지 않은 것만 탐색
    Optional<Item> findByIdAndDeletedFalse(Long id);

    //조건(카테고리, 지역, 판매 유형)에 맞는 상품을 페이징 처리하여 검색
    @Query(value = """
        SELECT i FROM Item i
        JOIN FETCH i.category c
        LEFT JOIN Shop s ON i.shopId = s.id
        WHERE (:categoryId IS NULL OR c.id = :categoryId)
          AND (:region IS NULL OR s.region = :region)
          AND (:saleType IS NULL OR i.saleType = :saleType)
          AND i.deleted = false
        """,
        countQuery = """
            SELECT COUNT(i) FROM Item i
            JOIN i.category c
            LEFT JOIN Shop s ON i.shopId = s.id
            WHERE (:categoryId IS NULL OR c.id = :categoryId)
              AND (:region IS NULL OR s.region = :region)
              AND (:saleType IS NULL OR i.saleType = :saleType)
              AND i.deleted = false
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
