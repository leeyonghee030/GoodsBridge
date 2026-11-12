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

    /**
     * 상품 ID로 삭제되지 않은 상품 조회
     */
    Optional<Item> findByIdAndDeletedFalse(Long id);

    /**
     * 판매자 ID로 삭제되지 않은 상품 목록 조회
     */
    List<Item> findBySellerUserIdAndDeletedFalse(Long userId);

    /**
     * 카테고리에 속한 삭제되지 않은 상품 개수 조회
     */
    Long countByCategoryIdAndDeletedFalse(Long categoryId);

    /**
     * 상품 검색 및 필터링
     *
     * 카테고리, 지역, 판매 유형을 조건으로 상품을 검색합니다.
     */
    @Query("""
        SELECT i
        FROM Item i
        JOIN FETCH i.category c
        LEFT JOIN Shop s ON i.shopId = s.id
        WHERE (:categoryId IS NULL OR c.id = :categoryId)
          AND (:region IS NULL OR s.region = :region)
          AND (:saleType IS NULL OR i.saleType = :saleType)
          AND i.deleted = false
        """)
    Page<Item> searchItemsWithFilter(
        @Param("categoryId") Long categoryId,
        @Param("region") String region,
        @Param("saleType") SaleType saleType,
        Pageable pageable
    );

    /**
     * 가게 삭제 시 소속 상품 전체 소프트 삭제
     *
     * 구독 취소 및 만료에서는 사용하지 않습니다.
     */
    @Modifying(
        clearAutomatically = true,
        flushAutomatically = true
    )
    @Query("""
        UPDATE Item i
           SET i.deleted = true,
               i.deletedAt = :now,
               i.status = :closed,
               i.pausedBySubscription = false,
               i.version = i.version + 1
         WHERE i.shopId = :shopId
           AND i.deleted = false
        """)
    int softDeleteByShopId(
        @Param("shopId") Long shopId,
        @Param("now") LocalDateTime now,
        @Param("closed") ItemStatus closed
    );

    /**
     * 구독 취소 및 만료 시 상품 판매 중지
     *
     * 현재 ON_SALE 상태인 상품만 CLOSED로 변경합니다.
     * 구독 때문에 중지된 상품임을 pausedBySubscription으로 표시합니다.
     */
    @Modifying(
        clearAutomatically = true,
        flushAutomatically = true
    )
    @Query("""
        UPDATE Item i
           SET i.status = :closed,
               i.pausedBySubscription = true,
               i.version = i.version + 1
         WHERE i.shopId = :shopId
           AND i.status = :onSale
           AND i.deleted = false
        """)
    int pauseItemsBySubscription(
        @Param("shopId") Long shopId,
        @Param("onSale") ItemStatus onSale,
        @Param("closed") ItemStatus closed
    );

    /**
     * 구독 재활성화 시 상품 판매 재개
     *
     * 구독으로 중지된 상품만 ON_SALE로 복구합니다.
     * 점주가 직접 CLOSED 처리한 상품은 복구하지 않습니다.
     */
    @Modifying(
        clearAutomatically = true,
        flushAutomatically = true
    )
    @Query("""
        UPDATE Item i
           SET i.status = :onSale,
               i.pausedBySubscription = false,
               i.version = i.version + 1
         WHERE i.shopId = :shopId
           AND i.pausedBySubscription = true
           AND i.deleted = false
        """)
    int resumeItemsBySubscription(
        @Param("shopId") Long shopId,
        @Param("onSale") ItemStatus onSale
    );
}
