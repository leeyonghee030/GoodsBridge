package com.goodsbridge.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CategoryServiceIntegrationTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryJpaRepository categoryRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Test
    @DisplayName("카테고리 목록 조회 시 N+1 없이 가나다순으로 정렬되며, 삭제되지 않은 상품 개수만 정확히 집계된다")
    void findAll_withItemCount_success() {
        // given: 가나다 순 정렬 테스트를 위해 무작위 순서로 카테고리 생성
        Category catDigital = categoryRepository.save(Category.builder().name("디지털").build());
        Category catFood = categoryRepository.save(Category.builder().name("식품").build());
        Category catEmpty = categoryRepository.save(Category.builder().name("가전").build()); // 상품이 없는 빈 카테고리

        Long dummySellerId = 1L;

        // 디지털 카테고리에 유효한 상품 2개 생성
        itemRepository.save(Item.builder()
            .sellerUserId(dummySellerId)
            .category(catDigital)
            .title("노트북")
            .price(1200000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build());

        itemRepository.save(Item.builder()
            .sellerUserId(dummySellerId)
            .category(catDigital)
            .title("스마트폰")
            .price(900000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build());

        // 삭제된 상품 생성 (내부 deleteItem 메서드를 활용하여 soft delete 상태로 구축)
        Item deletedItem = Item.builder()
            .sellerUserId(dummySellerId)
            .category(catDigital)
            .title("구형 폰")
            .price(100000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build();
        deletedItem.deleteItem(); // 소프트 삭제 처리 (deleted = true, deletedAt 설정)
        itemRepository.save(deletedItem);

        // 식품 카테고리에 유효한 상품 1개 생성
        itemRepository.save(Item.builder()
            .sellerUserId(dummySellerId)
            .category(catFood)
            .title("사과")
            .price(10000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build());

        // when: 서비스의 findAll 호출 (N+1 최적화 쿼리 실행)
        List<CategoryResponse> results = categoryService.findAll();

        // then: 검증
        assertThat(results).hasSize(3);

        // 1. 가나다 순 정렬 검증 ('가전' -> '디지털' -> '식품')
        assertThat(results.get(0).getName()).isEqualTo("가전");
        assertThat(results.get(1).getName()).isEqualTo("디지털");
        assertThat(results.get(2).getName()).isEqualTo("식품");

        // 2. 카테고리별 유효 상품 개수 집계 검증
        CategoryResponse resEmpty = results.stream().filter(r -> r.getName().equals("가전")).findFirst().get();
        assertThat(resEmpty.getItemCount()).isEqualTo(0L);

        CategoryResponse resDigital = results.stream().filter(r -> r.getName().equals("디지털")).findFirst().get();
        assertThat(resDigital.getItemCount()).isEqualTo(2L); // 삭제된 '구형 폰'은 제외되고 2개만 집계됨

        CategoryResponse resFood = results.stream().filter(r -> r.getName().equals("식품")).findFirst().get();
        assertThat(resFood.getItemCount()).isEqualTo(1L);
    }
}
