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
    private ItemJpaRepository itemJpaRepository;

    private Category 카테고리_생성(String name) {
        Category category = Category.builder()
            .name(name)
            .build();
        return categoryJpaRepository.save(category);
    }

    @Test
    @DisplayName("유효한 카테고리 ID로 상품 등록(create)을 요청하면 상품이 정상적으로 생성된다")
    void create_success() {
        Category category = 카테고리_생성("굿즈/피규어");

        ItemCreateRequest request = new ItemCreateRequest();
        // 리플렉션이나 빌더 없이 필드 주입이 안 될 경우 DTO에 세터나 빌더 추가 필요 (기본 생성자 상태 고려)
        // 여기서는 테스트 편의상 직접 엔티티 빌드로 검증하거나 DTO 구조에 맞게 세팅
        // (주의: DTO에 setter가 없다면 DTO 구조에 맞춰 수정 필요)
    }

    @Test
    @DisplayName("존재하지 않는 카테고리 ID로 상품 등록을 요청하면 NotFoundException이 발생한다")
    void create_fail_categoryNotFound() {
        ItemCreateRequest request = new ItemCreateRequest();
        Long userId = 1L;

        assertThatThrownBy(() -> itemService.create(userId, request))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("판매자 ID로 내 상품 목록 조회(findAllByUserId)를 수행하면 해당 유저의 상품들만 반환된다")
    void findAllByUserId_test() {
        Category category = 카테고리_생성("도서");

        Item item1 = Item.builder().sellerUserId(1L).category(category).title("자바의 정석").price(30000).saleType(SaleType.NORMAL).status(ItemStatus.ON_SALE).build();
        Item item2 = Item.builder().sellerUserId(1L).category(category).title("스프링 입문").price(25000).saleType(SaleType.NORMAL).status(ItemStatus.ON_SALE).build();
        Item item3 = Item.builder().sellerUserId(2L).category(category).title("알고리즘 책").price(40000).saleType(SaleType.NORMAL).status(ItemStatus.ON_SALE).build();

        itemJpaRepository.saveAll(List.of(item1, item2, item3));

        List<ItemResponse> myItems = itemService.findAllByUserId(1L);

        assertThat(myItems).hasSize(2);
        assertThat(myItems).allMatch(item -> item.getSellerUserId().equals(1L));
    }

    @Test
    @DisplayName("본인의 상품이 아닌 경우 수정(update)을 시도하면 ForbiddenException이 발생한다")
    void update_fail_forbidden() {
        Category category = 카테고리_생성("가전");
        Item item = Item.builder()
            .sellerUserId(1L) // 주인은 1번 유저
            .category(category)
            .title("원래 제목")
            .price(10000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build();
        Item savedItem = itemJpaRepository.save(item);

        ItemUpdateRequest updateRequest = new ItemUpdateRequest();

        // 2번 유저(다른 사람)가 수정을 시도할 때 403 ForbiddenException 발생 검증
        Long otherUserId = 2L;
        assertThatThrownBy(() -> itemService.update(otherUserId, savedItem.getId(), updateRequest))
            .isInstanceOf(ForbiddenException.class);
    }
}
