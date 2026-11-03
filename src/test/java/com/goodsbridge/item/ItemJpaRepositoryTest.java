package com.goodsbridge.item;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ItemJpaRepositoryTest {

    @Autowired
    private ItemJpaRepository itemJpaRepository;

    @Autowired
    private CategoryJpaRepository categoryJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("아이템을 ID로 조회하면 data.sql에 미리 등록된 데이터가 정상적으로 반환된다")
    void findById_test() {
        // given: data.sql에 등록된 ID 1번 아이템 조회
        Optional<Item> result = itemJpaRepository.findById(1L);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("한정판 피규어");
        assertThat(result.get().getPrice()).isEqualTo(45000);
        assertThat(result.get().getCategory().getName()).isEqualTo("굿즈/피규어");
        assertThat(result.get().getCreatedAt()).isNotNull(); // LocalDateTime 검증 추가
    }

    @Test
    @DisplayName("판매자 회원 ID(sellerUserId)로 내 상품 목록을 조회하면 해당 유저의 상품들만 반환된다")
    void findBySellerUserId_test() {
        // when: 1번 유저의 상품 목록 조회 (data.sql에 1번 유저 상품 2개 존재)
        List<Item> myItems = itemJpaRepository.findBySellerUserId(1L);

        // then
        assertThat(myItems).hasSize(2);
        assertThat(myItems).allMatch(item -> item.getSellerUserId().equals(1L));
    }

    @Test
    @DisplayName("카테고리 및 판매 타입 조건으로 필터링 및 페이징된 아이템 목록을 검색할 수 있다")
    void searchItemsWithFilter_test() {
        // given: 카테고리 ID 1번('굿즈/피규어'), NORMAL 타입
        Pageable pageable = PageRequest.of(0, 10);

        // when
        Page<Item> result = itemJpaRepository.searchItemsWithFilter(1L, SaleType.NORMAL, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("한정판 피규어");
    }

    @Test
    @DisplayName("영속성 컨텍스트 초기화 후 조회 시 Fetch 조인이 정상적으로 동작하여 Category가 함께 로딩된다")
    void fetchJoin_category_test() {
        // given
        entityManager.flush();
        entityManager.clear();

        // when
        Item foundItem = itemJpaRepository.findById(1L).orElseThrow();

        // then
        assertThat(foundItem.getCategory()).isNotNull();
        assertThat(foundItem.getCategory().getName()).isEqualTo("굿즈/피규어");
    }
}
