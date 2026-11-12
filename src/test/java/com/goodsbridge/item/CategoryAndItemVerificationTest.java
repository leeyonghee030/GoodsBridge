package com.goodsbridge.item;

import com.goodsbridge._core.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryAndItemVerificationTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private CategoryJpaRepository categoryRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Test
    @DisplayName("1. 카테고리 등록 시 중복 이름 예외 및 상품이 등록된 카테고리 삭제 방어 예외 검증")
    void category_duplicate_and_delete_restriction_test() throws Exception {
        // given: 새로운 고유 카테고리 생성 요청 DTO 설정
        CategoryCreateRequest createRequest = new CategoryCreateRequest();
        setField(createRequest, "name", "신규유니크카테고리");

        // 첫 번째 생성은 정상 성공 (생성된 ID 반환)
        Long categoryId = categoryService.create(createRequest);

        // 1-1. [중복 예외 검증] 동일한 이름으로 다시 생성 시도 시 BadRequestException 발생 확인
        CategoryCreateRequest duplicateRequest = new CategoryCreateRequest();
        setField(duplicateRequest, "name", "신규유니크카테고리");

        assertThatThrownBy(() -> categoryService.create(duplicateRequest))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("이미 존재하는 카테고리 이름입니다.");

        // 1-2. [삭제 방어 예외 검증] 해당 카테고리에 속한 상품이 존재할 때 삭제 시도 시 예외 발생 확인
        Category savedCategory = categoryRepository.findById(categoryId).orElseThrow();
        Item dummyItem = Item.builder()
            .sellerUserId(1L)
            .shopId(1L)
            .category(savedCategory)
            .title("테스트 상품")
            .price(10000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build();
        itemRepository.save(dummyItem);

        // 상품이 존재하는 상태에서 카테고리 삭제 시도 -> BadRequestException 발생해야 함
        assertThatThrownBy(() -> categoryService.delete(categoryId))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("상품이 등록되어 있는 카테고리는 삭제할 수 없습니다.");
    }

    @Test
    @DisplayName("2. 카테고리 목록 조회 시 가나다순 정렬 및 각 카테고리별 상품 개수 집계 검증")
    void category_sorting_and_item_count_test() {
        // given: data.sql의 기존 카테고리(ID 1: 굿즈/피규어, ID 2: 의류 등) 중 '의류'(ID: 2)에 상품 추가
        Category clothingCategory = categoryRepository.findById(2L).orElseThrow();

        itemRepository.save(Item.builder().sellerUserId(1L).shopId(1L).category(clothingCategory).title("추가된 의류 상품 1").price(15000).saleType(SaleType.NORMAL).build());

        // when: 카테고리 전체 목록 조회 (가나다순 정렬)
        List<CategoryResponse> responses = categoryService.findAll();

        // then: 가나다순 정렬 검증 (가전 -> 굿즈/피규어 -> 도서 -> 의류 순서)
        List<String> names = responses.stream()
            .map(CategoryResponse::getName)
            .toList();

        // 가나다순 정렬 확인: '가전' < '굿즈/피규어' < '도서' < '의류'
        assertThat(names).isSortedAccordingTo(String::compareTo);

        // '의류' 카테고리의 상품 개수 집계 검증 (data.sql 기본 2개 + 방금 추가한 상품 1개 = 총 3개)
        CategoryResponse clothingResponse = responses.stream()
            .filter(r -> r.getName().equals("의류"))
            .findFirst()
            .orElseThrow();

        assertThat(clothingResponse.getItemCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("3. 페이징 요청 시 최대 사이즈 제한(Max Size = 100) 초과 시 정상적으로 제어되는지 검증")
    void item_paging_max_size_limit_test() {
        // given: 1000개의 큰 사이즈를 요청하는 Pageable 생성
        Pageable excessivePageable = PageRequest.of(0, 1000);

        // when: ItemService의 findAll 호출 (내부적으로 MAX_PAGE_SIZE = 100 제한 로직 동작)
        Page<ItemResponse> resultPage = itemService.findAll(null, null, null, excessivePageable);

        // then: 요청한 페이지 사이즈가 1000이더라도 결과 페이지의 실제 페이지 사이즈는 100으로 제한되어야 함
        assertThat(resultPage.getPageable().getPageSize()).isEqualTo(100);
    }

    // private 필드 세팅을 위한 헬퍼 메서드
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
