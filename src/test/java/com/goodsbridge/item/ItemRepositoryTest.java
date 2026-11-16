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
class ItemRepositoryTest {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CategoryJpaRepository categoryJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("아이템을 ID로 조회하면 data.sql에 미리 등록된 데이터가 정상적으로 반환된다")
    void findById_test() {
        Optional<Item> result = itemRepository.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("한정판 피규어");
        assertThat(result.get().getPrice()).isEqualTo(45000);
        assertThat(result.get().getCategory().getName()).isEqualTo("굿즈/피규어");
        assertThat(result.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("판매자 회원 ID(sellerUserId)로 내 상품 목록을 조회하면 해당 유저의 상품들만 반환된다")
    void findBySellerUserId_test() {
        List<Item> myItems = itemRepository.findBySellerUserIdAndDeletedFalse(1L);

        assertThat(myItems).isNotEmpty();
        assertThat(myItems).allMatch(item -> item.getSellerUserId().equals(1L));
    }

    @Test
    @DisplayName("카테고리 및 판매 타입 조건으로 필터링 및 페이징된 아이템 목록을 검색할 수 있다")
    void searchItemsWithFilter_test() {
        Pageable pageable = PageRequest.of(0, 10);

        // data.sql에 존재하는 카테고리 ID 1L(굿즈/피규어)과 SaleType.NORMAL로 검색
        Page<Item> result = itemRepository.searchItemsWithFilter(1L, null, SaleType.NORMAL, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent())
            .extracting(Item::getTitle)
            .contains("홍대 굿즈샵 한정 피규어"); // data.sql의 실제 데이터명과 일치
    }

    @Test
    @DisplayName("영속성 컨텍스트 초기화 후 조회 시 Fetch 조인이 정상적으로 동작하여 Category가 함께 로딩된다")
    void fetchJoin_category_test() {
        entityManager.flush();
        entityManager.clear();

        Item foundItem = itemRepository.findById(1L).orElseThrow();

        assertThat(foundItem.getCategory()).isNotNull();
        assertThat(foundItem.getCategory().getName()).isEqualTo("굿즈/피규어");
    }
}
