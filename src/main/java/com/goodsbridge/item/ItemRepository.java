package com.goodsbridge.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByIdAndDeletedFalse(Long id);

    List<Item> findBySellerUserIdAndDeletedFalse(Long userId);

    // 💡 누락된 메서드 추가 (CategoryService에서 사용)
    Long countByCategoryIdAndDeletedFalse(Long categoryId);

    // 💡 오타 수정: IS NULL C -> IS NULL
    @Query("""
        SELECT i FROM Item i
        JOIN FETCH i.category c
        LEFT JOIN Shop s ON i.shopId = s.id
        WHERE (:categoryId IS NULL OR c.id = :categoryId)
          AND (:region IS NULL OR s.region = :region)
          AND (:saleType IS NULL OR i.saleType = :saleType)
          AND (:keyword IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND i.deleted = false
        """)
    Page<Item> searchItemsWithFilterAndKeyword(
        @Param("categoryId") Long categoryId,
        @Param("region") String region,
        @Param("saleType") SaleType saleType,
        @Param("keyword") String keyword,
        Pageable pageable
    );

    // SHOP 목록용: 가게 상품만 (교환용 개인 상품 제외), 판매중/판매완료만, status 가 있으면 그 상태만
    @Query("""
        SELECT i FROM Item i
        JOIN FETCH i.category c
        LEFT JOIN Shop s ON i.shopId = s.id
        WHERE i.shopId IS NOT NULL
          AND i.status IN (com.goodsbridge.item.ItemStatus.ON_SALE, com.goodsbridge.item.ItemStatus.SOLD)
          AND (:status IS NULL OR i.status = :status)
          AND (:categoryId IS NULL OR c.id = :categoryId)
          AND (:region IS NULL OR s.region = :region)
          AND (:saleType IS NULL OR i.saleType = :saleType)
          AND (:keyword IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND i.deleted = false
        """)
    Page<Item> searchShopItems(
        @Param("categoryId") Long categoryId,
        @Param("region") String region,
        @Param("saleType") SaleType saleType,
        @Param("keyword") String keyword,
        @Param("status") ItemStatus status,
        Pageable pageable
    );

    // 제목 검색어 없이 조회 (기존 호출부 호환)
    default Page<Item> searchItemsWithFilter(Long categoryId, String region, SaleType saleType, Pageable pageable) {
        return searchItemsWithFilterAndKeyword(categoryId, region, saleType, null, pageable);
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Item i
        SET i.deleted = true, i.deletedAt = :now, i.status = 'CLOSED'
        WHERE i.shopId = :shopId AND i.deleted = false
        """)
    void softDeleteByShopId(@Param("shopId") Long shopId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Item i
        SET i.status = :targetStatus
        WHERE i.shopId = :shopId AND i.status = :sourceStatus
        """)
    void updateStatusByShopIdAndStatus(
        @Param("shopId") Long shopId,
        @Param("sourceStatus") ItemStatus sourceStatus,
        @Param("targetStatus") ItemStatus targetStatus
    );

    @Query("SELECT i FROM Item i WHERE i.shopId = :shopId AND i.deletedAt IS NOT NULL")
    List<Item> findAllDeletedByShopId(@Param("shopId") Long shopId);
}
