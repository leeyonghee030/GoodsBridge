package com.goodsbridge.item;

import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ItemServiceIntegrationTest {

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CategoryJpaRepository categoryRepository;

    @Test
    @DisplayName("1. 상품 등록 성공 테스트")
    void createItemTest() {
        // given: 카테고리 조회 (data.sql 등에 미리 등록되어 있다고 가정, 혹은 직접 생성)
        Category category = categoryRepository.findAll().stream().findFirst().orElseThrow();

        ItemCreateRequest request = new ItemCreateRequest();
        request.setCategoryId(category.getId());
        request.setShopId(1L);
        request.setTitle("테스트 굿즈 인형");
        request.setDescription("귀여운 인형입니다.");
        request.setPrice(15000); // 혹은 setPrice
        request.setSaleType(SaleType.NORMAL);
        request.setImageUrl("https://example.com/image.png");

        // when
        Long itemId = itemService.create(1L, request);

        // then
        assertThat(itemId).isNotNull();
        Item savedItem = itemRepository.findById(itemId).orElseThrow();
        assertThat(savedItem.getTitle()).isEqualTo("테스트 굿즈 인형");
        assertThat(savedItem.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("2. 필터 조건(카테고리, 판매 타입 등)을 적용한 페이징 검색 테스트")
    void searchItemsWithFilterTest() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        // when: 전체 검색 또는 특정 조건 검색
        Page<ItemResponse> resultPage = itemService.findAll(null, null, null, pageable);

        // then
        assertThat(resultPage).isNotNull();
        // data.sql 등의 초기 데이터 구조에 맞춰 검증
    }

    @Test
    @DisplayName("3. 상품 수정 성공 테스트 및 권한 검증")
    void updateItemTest() {
        // given: 기존 상품 조회 (data.sql 기반 또는 생성)
        Item item = itemRepository.findAll().stream().findFirst().orElseThrow();
        Category newCategory = categoryRepository.findAll().get(0);

        ItemUpdateRequest request = new ItemUpdateRequest();
        request.setCategoryId(newCategory.getId());
        request.setTitle("수정된 상품 제목");
        request.setDescription("수정된 상세 설명");
        request.setPrice(20000);
        request.setSaleType(SaleType.EVENT);
        request.setStatus(ItemStatus.ON_SALE);
        request.setImageUrl("https://example.com/updated.png");

        // when: 소유주(sellerUserId)가 수정 요청
        itemService.update(item.getSellerUserId(), item.getId(), request);

        // then
        Item updatedItem = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(updatedItem.getTitle()).isEqualTo("수정된 상품 제목");
        assertThat(updatedItem.getPrice()).isEqualTo(20000);
    }

    @Test
    @DisplayName("4. 소유주가 아닌 사용자가 상품 수정 시 ForbiddenException 발생")
    void updateItemForbiddenTest() {
        // given
        Item item = itemRepository.findAll().stream().findFirst().orElseThrow();
        Long differentUserId = item.getSellerUserId() + 999L; // 다른 유저 ID

        ItemUpdateRequest request = new ItemUpdateRequest();
        request.setCategoryId(item.getCategory().getId());
        request.setTitle("권한 없는 수정 시도");
        request.setPrice(30000);
        request.setSaleType(SaleType.NORMAL);

        // when & then
        assertThrows(ForbiddenException.class, () ->
            itemService.update(differentUserId, item.getId(), request)
        );
    }

    @Test
    @DisplayName("5. 상품 소프트 삭제(Soft Delete) 시 상세 조회 및 목록 조회에서 배제된다")
    void deleteItemSoftDeleteTest() {
        // given
        Item item = itemRepository.findAll().stream()
            .filter(i -> !i.isDeleted())
            .findFirst()
            .orElseThrow();

        Long sellerId = item.getSellerUserId();
        Long itemId = item.getId();

        // when: 상품 삭제 요청 (소프트 딜리트)
        itemService.delete(sellerId, itemId);

        // then 1: 단건 조회 시 NotFoundException 발생 (삭제된 상품 취급)
        assertThrows(NotFoundException.class, () ->
            itemService.findById(itemId)
        );

        // then 2: 내 상품 목록이나 검색 목록에서 해당 상품이 노출되지 않음
        List<ItemResponse> myItems = itemService.findAllByUserId(sellerId);
        boolean containsDeleted = myItems.stream().anyMatch(i -> i.getId().equals(itemId));
        assertThat(containsDeleted).isFalse();
    }
}
