package com.goodsbridge.item;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryJpaRepository extends JpaRepository<Category, Long> {
    // 이름으로 카테고리 존재 여부 확인 (등록 시 중복 방지용)
    boolean existsByName(String name);

    // 카테고리 수정 시 검증용 (자기 자신을 제외하고 같은 이름이 이미 존재하는지 확인)
    boolean existsByNameAndIdNot(String name, Long id);

    // 이름으로 카테고리 단건 조회
    Optional<Category> findByName(String name);

    // 카테고리 목록을 이름 오름차순(가나다순)으로 정렬하여 조회
    List<Category> findAllByOrderByNameAsc();

    // 구매 건수 기준 인기 카테고리 (동률/0건이면 id 오름차순으로 폴백)
    @Query("""
        select c from Category c
        left join Purchase p on p.item.category = c
        group by c
        order by count(p) desc, c.id asc
        """)
    List<Category> findTopByPurchaseCount(Pageable pageable);

    // 교환 게시글 등록 건수 기준 인기 카테고리 (동률/0건이면 id 오름차순으로 폴백)
    @Query("""
        select c from Category c
        left join ExchangeItem e on e.item.category = c
        group by c
        order by count(e) desc, c.id asc
        """)
    List<Category> findTopByExchangeCount(Pageable pageable);
}
