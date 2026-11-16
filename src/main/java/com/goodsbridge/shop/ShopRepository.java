package com.goodsbridge.shop;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    /**
     * 가게 ID로 조회하면서 비관적 쓰기 락 적용
     *
     * 가게 삭제 및 구독 처리 과정에서 동시성 문제를 방지합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM Shop s
        WHERE s.id = :id
        """)
    Optional<Shop> findByIdWithLock(@Param("id") Long id);

    /**
     * 고객용 가게 목록 조회
     *
     * 삭제되지 않고 승인된 가게만 조회합니다.
     */
    @Query(
        value = """
            SELECT s
            FROM Shop s
            WHERE (:name IS NULL OR s.name LIKE %:name%)
              AND (:region IS NULL OR s.region = :region)
              AND s.status = com.goodsbridge.shop.ShopStatus.APPROVED
              AND s.deleted = false
            """,
        countQuery = """
            SELECT COUNT(s)
            FROM Shop s
            WHERE (:name IS NULL OR s.name LIKE %:name%)
              AND (:region IS NULL OR s.region = :region)
              AND s.status = com.goodsbridge.shop.ShopStatus.APPROVED
              AND s.deleted = false
            """
    )
    Page<Shop> searchApprovedShops(
        @Param("name") String name,
        @Param("region") String region,
        Pageable pageable
    );

    /**
     * 사장님 본인 가게 목록 조회
     *
     * 삭제되지 않은 가게만 조회합니다.
     */
    Page<Shop> findAllByOwnerUserIdAndDeletedFalse(
        Long ownerUserId,
        Pageable pageable
    );

    // 삭제되지 않은 가게를 가진 사장님인지 (가게 계정은 구매/교환/이벤트/경매 참여를 막는 데 쓴다)
    boolean existsByOwnerUserIdAndDeletedFalse(Long ownerUserId);

    /**
     * 관리자용 가게 상태별 조회
     *
     * status가 null이면 전체 상태를 조회합니다.
     */
    @Query(
        value = """
            SELECT s
            FROM Shop s
            WHERE (:status IS NULL OR s.status = :status)
              AND s.deleted = false
            """,
        countQuery = """
            SELECT COUNT(s)
            FROM Shop s
            WHERE (:status IS NULL OR s.status = :status)
              AND s.deleted = false
            """
    )
    Page<Shop> findAllByStatus(
        @Param("status") ShopStatus status,
        Pageable pageable
    );

    /**
     * 관리자용 통합 검색
     *
     * 가게 이름, 지역, 상태 조건을 조합하여 조회합니다.
     */
    @Query(
        value = """
            SELECT s
            FROM Shop s
            WHERE (:name IS NULL OR s.name LIKE %:name%)
              AND (:region IS NULL OR s.region = :region)
              AND (:status IS NULL OR s.status = :status)
              AND s.deleted = false
            """,
        countQuery = """
            SELECT COUNT(s)
            FROM Shop s
            WHERE (:name IS NULL OR s.name LIKE %:name%)
              AND (:region IS NULL OR s.region = :region)
              AND (:status IS NULL OR s.status = :status)
              AND s.deleted = false
            """
    )
    Page<Shop> searchShopsByAdmin(
        @Param("name") String name,
        @Param("region") String region,
        @Param("status") ShopStatus status,
        Pageable pageable
    );
    /**
     * 삭제되지 않은 가게 ID를 커서 방식으로 조회
     */
    @Query("""
    SELECT s.id
    FROM Shop s
    WHERE s.id > :lastId
      AND s.deleted = false
    ORDER BY s.id ASC
    """)
    List<Long> findNotDeletedShopIdsAfterId(
        @Param("lastId") Long lastId,
        Pageable pageable
    );

}
