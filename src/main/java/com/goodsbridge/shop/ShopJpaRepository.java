package com.goodsbridge.shop;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShopJpaRepository extends JpaRepository<Shop, Long> {
    // 디렉토리 조회 (이름 부분일치, 지역, 카테고리/상태 등 필터 확장 가능)
    @Query("""
        SELECT s FROM Shop s WHERE (:name IS NULL OR s.name LIKE %:name%)
        AND (:region IS NULL OR s.region = :region)
        AND s.status = 'APPROVED'
        """)
    List<Shop> searchApprovedShops(@Param("name") String name, @Param("region") String region);

    // 내 가게 목록 조회
    List<Shop> findAllByOwnerUserId(Long ownerUserId);

    // 관리자 입점 대기 목록 조회
    @Query("""
        SELECT s FROM Shop s WHERE (:status IS NULL OR s.status = :status)
        """)
    List<Shop> findAllByStatus(ShopStatus status);

}
