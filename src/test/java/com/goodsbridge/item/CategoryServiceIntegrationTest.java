package com.goodsbridge.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
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
        // given: data.sql과 겹치지 않는 유니크한 테스트용 카테고리 생성 (가나다 순 정렬 확인용)
        Category catDigital = categoryRepository.save(Category.builder().name("테스트디지털").build());
        Category catFood = categoryRepository.save(Category.builder().name("테스트식품").build());
        Category catEmpty = categoryRepository.save(Category.builder().name("테스트가전").build()); // 상품이 없는 빈 카테고리

        Long dummySellerId = 1L;
        Long dummyShopId = 1L;

        // 테스트디지털 카테고리에 유효한 상품 2개 생성
        itemRepository.save(Item.builder()
            .sellerUserId(dummySellerId)
            .shopId(dummyShopId)
            .category(catDigital)
            .title("노트북")
            .price(1200000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build());

        itemRepository.save(Item.builder()
            .sellerUserId(dummySellerId)
            .shopId(dummyShopId)
            .category(catDigital)
            .title("스마트폰")
            .price(900000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build());

        // 삭제된 상품 생성 (소프트 딜리트 상태)
        Item deletedItem = Item.builder()
            .sellerUserId(dummySellerId)
            .shopId(dummyShopId)
            .category(catDigital)
            .title("구형 폰")
            .price(100000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build();
        deletedItem.deleteItem();
        itemRepository.save(deletedItem);

        // 테스트식품 카테고리에 유효한 상품 1개 생성
        itemRepository.save(Item.builder()
            .sellerUserId(dummySellerId)
            .shopId(dummyShopId)
            .category(catFood)
            .title("사과")
            .price(10000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build());

        // when: 서비스의 findAll 호출
        List<CategoryResponse> results = categoryService.findAll();

        // then: 검증
        // 1. 전체 목록 중 우리가 생성한 테스트 카테고리들이 가나다순으로 잘 포함되어 있는지 확인
        List<String> testCategoryNames = results.stream()
            .map(CategoryResponse::getName)
            .filter(name -> name.startsWith("테스트"))
            .toList();

        assertThat(testCategoryNames).containsExactly("테스트가전", "테스트디지털", "테스트식품");

        // 2. 카테고리별 유효 상품 개수 집계 검증 (삭제된 상품은 제외되어야 함)
        CategoryResponse resEmpty = results.stream().filter(r -> r.getName().equals("테스트가전")).findFirst().get();
        assertThat(resEmpty.getItemCount()).isEqualTo(0L);

        CategoryResponse resDigital = results.stream().filter(r -> r.getName().equals("테스트디지털")).findFirst().get();
        assertThat(resDigital.getItemCount()).isEqualTo(2L); // 삭제된 '구형 폰' 제외, 유효 상품 2개만 집계

        CategoryResponse resFood = results.stream().filter(r -> r.getName().equals("테스트식품")).findFirst().get();
        assertThat(resFood.getItemCount()).isEqualTo(1L);
    }
}
