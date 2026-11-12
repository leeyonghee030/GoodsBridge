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

@SpringBootTest // 💡 webEnvironment 속성 제거 (기본 MOCK 환경 사용으로 시큐리티 빈 정상 로드)
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
    @DisplayName("1. 상품 등록 성공 테스트 (data.sql의 활성 샵 ID 1L 활용)")
    void createItemTest() {
        Category category = categoryRepository.findAll().stream().findFirst().orElseThrow();

        ItemCreateRequest request = new ItemCreateRequest();
        request.setCategoryId(category.getId());
        request.setShopId(1L); // data.sql에 구독이 활성화되어 있는 '홍대 굿즈샵' ID
        request.setTitle("테스트 굿즈 인형");
        request.setDescription("귀여운 인형입니다.");
        request.setPrice(15000);
        request.setSaleType(SaleType.NORMAL);
        request.setImageUrl("https://example.com/image.png");

        Long userId = 1L; // data.sql의 가게사장 유저 ID
        Long itemId = itemService.create(userId, request);

        assertThat(itemId).isNotNull();
        Item savedItem = itemRepository.findById(itemId).orElseThrow();
        assertThat(savedItem.getTitle()).isEqualTo("테스트 굿즈 인형");
        assertThat(savedItem.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("2. 필터 조건(카테고리, 판매 타입 등)을 적용한 페이징 검색 테스트")
    void searchItemsWithFilterTest() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<ItemResponse> resultPage = itemService.findAll(null, null, null, pageable);

        assertThat(resultPage).isNotNull();
        assertThat(resultPage.getContent()).isNotEmpty();
    }

    @Test
    @DisplayName("3. 상품 수정 성공 테스트 및 권한 검증")
    void updateItemTest() {
        Item item = itemRepository.findAll().stream().findFirst().orElseThrow();
        Category newCategory = categoryRepository.findAll().get(0);

        ItemUpdateRequest request = new ItemUpdateRequest();
        request.setShopId(item.getShopId());
        request.setCategoryId(newCategory.getId());
        request.setTitle("수정된 상품 제목");
        request.setDescription("수정된 상세 설명");
        request.setPrice(20000);
        request.setSaleType(SaleType.EVENT);
        request.setStatus(ItemStatus.ON_SALE);
        request.setImageUrl("https://example.com/updated.png");

        itemService.update(item.getSellerUserId(), item.getId(), request);

        Item updatedItem = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(updatedItem.getTitle()).isEqualTo("수정된 상품 제목");
        assertThat(updatedItem.getPrice()).isEqualTo(20000);
    }

    @Test
    @DisplayName("4. 소유주가 아닌 사용자가 상품 수정 시 ForbiddenException 발생")
    void updateItemForbiddenTest() {
        Item item = itemRepository.findAll().stream().findFirst().orElseThrow();
        Long differentUserId = item.getSellerUserId() + 999L;

        ItemUpdateRequest request = new ItemUpdateRequest();
        request.setShopId(item.getShopId());
        request.setCategoryId(item.getCategory().getId());
        request.setTitle("권한 없는 수정 시도");
        request.setPrice(30000);
        request.setSaleType(SaleType.NORMAL);
        request.setStatus(ItemStatus.ON_SALE);

        assertThrows(ForbiddenException.class, () ->
            itemService.update(differentUserId, item.getId(), request)
        );
    }

    @Test
    @DisplayName("5. 상품 소프트 삭제(Soft Delete) 시 상세 조회 및 목록 조회에서 배제된다")
    void deleteItemSoftDeleteTest() {
        Item item = itemRepository.findAll().stream()
            .filter(i -> !i.isDeleted())
            .findFirst()
            .orElseThrow();

        Long sellerId = item.getSellerUserId();
        Long itemId = item.getId();

        itemService.delete(sellerId, itemId);

        assertThrows(NotFoundException.class, () ->
            itemService.findById(itemId)
        );

        List<ItemResponse> myItems = itemService.findAllByUserId(sellerId);
        boolean containsDeleted = myItems.stream().anyMatch(i -> i.getId().equals(itemId));
        assertThat(containsDeleted).isFalse();
    }

    @Test
    @DisplayName("6. 소프트 삭제된 상품을 복구(restore)하면 deleted가 false로 바뀌고 상태가 원복된다")
    void restoreItemTest() {
        Item item = itemRepository.findAll().stream()
            .filter(i -> !i.isDeleted())
            .findFirst()
            .orElseThrow();

        item.deleteItem();
        itemRepository.saveAndFlush(item);

        assertThat(item.isDeleted()).isTrue();
        assertThat(item.getStatus()).isEqualTo(ItemStatus.CLOSED);

        item.restore(ItemStatus.ON_SALE);
        itemRepository.saveAndFlush(item);

        assertThat(item.isDeleted()).isFalse();
        assertThat(item.getDeletedAt()).isNull();
        assertThat(item.getStatus()).isEqualTo(ItemStatus.ON_SALE);
    }
}
