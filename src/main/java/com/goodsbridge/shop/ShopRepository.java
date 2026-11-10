package com.goodsbridge.shop;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    // 💡 동시성 제어를 위한 비관적 락(Pessimistic Write) 조회 메서드 추가
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shop s WHERE s.id = :id")
    Optional<Shop> findByIdWithLock(@Param("id") Long id);

    // 1. 고객용 디렉터리 페이징 조회 (삭제되지 않고 승인된 가게만)
    @Query("""
        SELECT s FROM Shop s WHERE (:name IS NULL OR s.name LIKE %:name%)
        AND (:region IS NULL OR s.region = :region)
        AND s.status = 'APPROVED'
        AND s.deleted = false
        """)
    Page<Shop> searchApprovedShops(@Param("name") String name, @Param("region") String region, Pageable pageable);

    // 2. 사장님 본인 가게 목록 조회 (삭제되지 않은 것만)
    Page<Shop> findAllByOwnerUserIdAndDeletedFalse(Long ownerUserId, Pageable pageable);

    // 3. 관리자용 상태별 페이징 조회 (삭제되지 않은 것만)
    @Query("""
        SELECT s FROM Shop s WHERE (:status IS NULL OR s.status = :status)
        AND s.deleted = false
        """)
    Page<Shop> findAllByStatus(@Param("status") ShopStatus status, Pageable pageable);

    // 4. 관리자용 통합 동적 검색 페이징
    @Query("""
        SELECT s FROM Shop s WHERE (:name IS NULL OR s.name LIKE %:name%)
        AND (:region IS NULL OR s.region = :region)
        AND (:status IS NULL OR s.status = :status)
        AND s.deleted = false
        """)
    Page<Shop> searchShopsByAdmin(@Param("name") String name,
                                  @Param("region") String region,
                                  @Param("status") ShopStatus status,
                                  Pageable pageable);
}
