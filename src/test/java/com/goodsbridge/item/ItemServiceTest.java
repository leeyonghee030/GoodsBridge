package com.goodsbridge.item;

import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ItemServiceTest {

    @Autowired
    private ItemService itemService;

    @Autowired
    private CategoryJpaRepository categoryJpaRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Test
    @DisplayName("유효한 카테고리 ID와 샵 ID로 상품 등록(create)을 요청하면 상품이 정상적으로 생성된다")
    void create_success() {
        ItemCreateRequest request = new ItemCreateRequest();
        request.setShopId(1L); // 💡 data.sql의 활성 구독 샵 ID 필수 지정
        request.setCategoryId(1L);
        request.setTitle("새로운 상품");
        request.setDescription("설명입니다.");
        request.setPrice(15000);
        request.setSaleType(SaleType.NORMAL);

        Long userId = 1L;

        Long savedId = itemService.create(userId, request);

        assertThat(savedId).isNotNull();
        Item foundItem = itemRepository.findById(savedId).orElseThrow();
        assertThat(foundItem.getTitle()).isEqualTo("새로운 상품");
        assertThat(foundItem.getPrice()).isEqualTo(15000);
        assertThat(foundItem.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 카테고리 ID로 상품 등록을 요청하면 NotFoundException이 발생한다")
    void create_fail_categoryNotFound() {
        ItemCreateRequest request = new ItemCreateRequest();
        request.setShopId(1L); // 💡 샵 ID 지정하여 카테고리 검증 단계까지 진입하도록 설정
        request.setCategoryId(9999L);
        request.setTitle("실패할 상품");
        request.setPrice(10000);
        request.setSaleType(SaleType.NORMAL);

        Long userId = 1L;

        assertThatThrownBy(() -> itemService.create(userId, request))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("판매자 ID로 내 상품 목록 조회(findAllByUserId)를 수행하면 해당 유저의 상품들만 반환된다")
    void findAllByUserId_test() {
        List<ItemResponse> myItems = itemService.findAllByUserId(1L);

        assertThat(myItems).isNotEmpty();
        assertThat(myItems).allMatch(item -> item.getSellerUserId().equals(1L));
    }

    @Test
    @DisplayName("본인의 상품이 아닌 경우 수정(update)을 시도하면 ForbiddenException이 발생한다")
    void update_fail_forbidden() {
        ItemUpdateRequest updateRequest = new ItemUpdateRequest();
        updateRequest.setShopId(1L); // 💡 필수 샵 ID 지정
        updateRequest.setCategoryId(1L);
        updateRequest.setTitle("수정 시도 제목");
        updateRequest.setPrice(20000);
        updateRequest.setSaleType(SaleType.NORMAL);
        updateRequest.setStatus(ItemStatus.ON_SALE); // 💡 필수 상태값 지정

        Long otherUserId = 2L;

        assertThatThrownBy(() -> itemService.update(otherUserId, 1L, updateRequest))
            .isInstanceOf(ForbiddenException.class);
    }
}
