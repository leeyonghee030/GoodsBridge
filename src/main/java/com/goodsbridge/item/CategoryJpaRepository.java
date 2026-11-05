package com.goodsbridge.item;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
