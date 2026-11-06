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

    // 1. 단건 상세 조회 (삭제되지 않은 상품만)
    Optional<Item> findByIdAndDeletedFalse(Long id);

    // 2. 내 상품 목록 조회 (삭제되지 않은 상품만)
    List<Item> findBySellerUserIdAndDeletedFalse(Long userId);

    // 3. 필터링 조건 검색 쿼리 (카테고리, 지역, 판매 타입 등)
    @Query("""
        SELECT i FROM Item i
        JOIN FETCH i.category c
        JOIN Shop s ON i.shopId = s.id
        WHERE (:categoryId IS NULL OR c.id = :categoryId)
          AND (:region IS NULL OR s.region = :region)
          AND (:saleType IS NULL C OR i.saleType = :saleType)
          AND i.deleted = false
        """)
    Page<Item> searchItemsWithFilter(
        @Param("categoryId") Long categoryId,
        @Param("region") String region,
        @Param("saleType") SaleType saleType,
        Pageable pageable
    );

    // 벌크 연산: 특정 가게의 모든 상품 일괄 마감 처리
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Item i
        SET i.deleted = true, i.deletedAt = :now, i.status = 'CLOSED'
        WHERE i.shopId = :shopId AND i.deleted = false
        """)
    void softDeleteByShopId(@Param("shopId") Long shopId, @Param("now") LocalDateTime now);

    // 벌크 연산: 상품 상태 일괄 변경
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

    // 소프트 삭제된 상품 목록 조회 (구독 복구용)
    @Query(value = "SELECT * FROM item_tb WHERE shop_id = :shopId AND deleted_at IS NOT NULL", nativeQuery = true)
    List<Item> findAllDeletedByShopId(@Param("shopId") Long shopId);
}
