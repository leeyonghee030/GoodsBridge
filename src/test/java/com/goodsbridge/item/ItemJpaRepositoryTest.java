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

    // 테스트용 카테고리 생성 헬퍼 메서드
    private Category 카테고리_생성(String name) {
        Category category = Category.builder()
            .name(name)
            .build();
        return categoryJpaRepository.save(category);
    }

    // 테스트용 아이템 생성 헬퍼 메서드 (sellerUserId 반영)
    private Item 아이템_생성(Long sellerUserId, Category category, String title, int price, SaleType saleType) {
        Item item = Item.builder()
            .sellerUserId(sellerUserId)
            .shopId(null) // 개인 판매 기본값
            .category(category)
            .title(title)
            .description("상세 설명 테스트")
            .price(price)
            .saleType(saleType)
            .status(ItemStatus.ON_SALE)
            .imageUrl("https://example.com/image.jpg")
            .build();
        return itemJpaRepository.save(item);
    }

    @Test
    @DisplayName("아이템을 저장하고 ID로 조회하면 정상적으로 반환된다")
    void save_and_findById_test() {
        // given
        Category category = 카테고리_생성("굿즈/피규어");
        Item savedItem = 아이템_생성(1L, category, "한정판 피규어", 45000, SaleType.NORMAL);

        // when
        Optional<Item> result = itemJpaRepository.findById(savedItem.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("한정판 피규어");
        assertThat(result.get().getPrice()).isEqualTo(45000);
        assertThat(result.get().getCategory().getName()).isEqualTo("굿즈/피규어");
    }

    @Test
    @DisplayName("판매자 회원 ID(sellerUserId)로 내 상품 목록을 조회하면 해당 유저의 상품들만 반환된다")
    void findBySellerUserId_test() {
        // given
        Category category = 카테고리_생성("의류");
        아이템_생성(1L, category, "후드티 A", 30000, SaleType.NORMAL);
        아이템_생성(1L, category, "바지 B", 40000, SaleType.NORMAL);
        아이템_생성(2L, category, "모자 C", 15000, SaleType.NORMAL);

        // when
        List<Item> myItems = itemJpaRepository.findBySellerUserId(1L);

        // then
        assertThat(myItems).hasSize(2);
        assertThat(myItems).allMatch(item -> item.getSellerUserId().equals(1L));
    }

    @Test
    @DisplayName("카테고리 및 판매 타입 조건으로 필터링 및 페이징된 아이템 목록을 검색할 수 있다")
    void searchItemsWithFilter_test() {
        // given
        Category cat1 = 카테고리_생성("도서");
        Category cat2 = 카테고리_생성("가전");

        아이템_생성(1L, cat1, "JPA 책", 25000, SaleType.NORMAL);
        아이템_생성(2L, cat1, "스프링 책", 35000, SaleType.NORMAL);
        아이템_생성(1L, cat2, "청소기", 50000, SaleType.EVENT);

        Pageable pageable = PageRequest.of(0, 10);

        // when: 카테고리와 판매 타입(NORMAL)으로 검색 (region은 null)
        Page<Item> result = itemJpaRepository.searchItemsWithFilter(cat1.getId(), null, SaleType.NORMAL, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(item -> item.getCategory().getId().equals(cat1.getId()));
    }

    @Test
    @DisplayName("영속성 컨텍스트 초기화 후 조회 시 Fetch 조인이 정상적으로 동작하여 Category가 함께 로딩된다")
    void fetchJoin_category_test() {
        // given
        Category category = 카테고리_생성("가구");
        Item item = 아이템_생성(1L, category, "책상", 80000, SaleType.NORMAL);

        // when
        entityManager.flush();
        entityManager.clear();

        Item foundItem = itemJpaRepository.findById(item.getId()).orElseThrow();

        // then
        assertThat(foundItem.getCategory()).isNotNull();
        assertThat(foundItem.getCategory().getName()).isEqualTo("가구");
    }
}
