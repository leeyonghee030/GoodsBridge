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
    @DisplayName("유효한 카테고리 ID로 상품 등록(create)을 요청하면 상품이 정상적으로 생성된다")
    void create_success() {
        // given: data.sql에 존재하는 카테고리 ID 1번 활용
        ItemCreateRequest request = new ItemCreateRequest();
        request.setCategoryId(1L);
        request.setTitle("새로운 상품");
        request.setDescription("설명입니다.");
        request.setPrice(15000);
        request.setSaleType(SaleType.NORMAL);

        Long userId = 1L;

        // when
        Long savedId = itemService.create(userId, request);

        // then
        assertThat(savedId).isNotNull();
        Item foundItem = itemRepository.findById(savedId).orElseThrow();
        assertThat(foundItem.getTitle()).isEqualTo("새로운 상품");
        assertThat(foundItem.getPrice()).isEqualTo(15000);
        assertThat(foundItem.getCreatedAt()).isNotNull(); // 생성일자(Auditing) 검증
    }

    @Test
    @DisplayName("존재하지 않는 카테고리 ID로 상품 등록을 요청하면 NotFoundException이 발생한다")
    void create_fail_categoryNotFound() {
        // given
        ItemCreateRequest request = new ItemCreateRequest();
        request.setCategoryId(9999L); // 존재하지 않는 ID
        request.setTitle("실패할 상품");
        request.setPrice(10000);
        request.setSaleType(SaleType.NORMAL);

        Long userId = 1L;

        // when & then
        assertThatThrownBy(() -> itemService.create(userId, request))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("판매자 ID로 내 상품 목록 조회(findAllByUserId)를 수행하면 해당 유저의 상품들만 반환된다")
    void findAllByUserId_test() {
        // given: data.sql에 1번 유저의 상품이 2개 미리 등록되어 있음

        // when
        List<ItemResponse> myItems = itemService.findAllByUserId(1L);

        // then
        assertThat(myItems).hasSize(2);
        assertThat(myItems).allMatch(item -> item.getSellerUserId().equals(1L));
    }

    @Test
    @DisplayName("본인의 상품이 아닌 경우 수정(update)을 시도하면 ForbiddenException이 발생한다")
    void update_fail_forbidden() {
        // given: data.sql에 등록된 1번 아이템(sellerUserId = 1L)을 2번 유저가 수정 시도
        ItemUpdateRequest updateRequest = new ItemUpdateRequest();
        updateRequest.setCategoryId(1L);
        updateRequest.setTitle("수정 시도 제목");
        updateRequest.setPrice(20000);
        updateRequest.setSaleType(SaleType.NORMAL);

        Long otherUserId = 2L; // 다른 유저 ID

        // when & then
        assertThatThrownBy(() -> itemService.update(otherUserId, 1L, updateRequest))
            .isInstanceOf(ForbiddenException.class);
    }
}
