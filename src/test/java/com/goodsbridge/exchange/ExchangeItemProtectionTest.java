package com.goodsbridge.exchange;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemService;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.item.ItemUpdateRequest;
import com.goodsbridge.item.SaleType;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 교환 게시글로 올라가 있는 상품은 삭제하거나 가격·상태·판매 종류를 바꿀 수 없고, 같은 상품으로 게시글을 또 올릴 수 없다.
 * 테스트 데이터: 상품 1(판매자 사용자 1, 가게 1), 사용자 1, 가게 1.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExchangeItemProtectionTest {
    @Autowired ItemService itemService;
    @Autowired ItemRepository itemRepository;
    @Autowired ExchangeItemRepository exchangeItemRepository;
    @Autowired UserRepository userRepository;
    @Autowired ShopRepository shopRepository;

    private ExchangeItem openPost() {
        User user = userRepository.findById(1L).orElseThrow();
        Item item = itemRepository.findById(1L).orElseThrow();
        Shop shop = shopRepository.findById(1L).orElseThrow();
        return exchangeItemRepository.save(ExchangeItem.builder().user(user).item(item).shop(shop).wantDescription("원하는 굿즈").build());
    }

    private ItemUpdateRequest update(int price, ItemStatus status, SaleType saleType, String title) {
        Item item = itemRepository.findById(1L).orElseThrow();
        ItemUpdateRequest request = new ItemUpdateRequest();
        request.setShopId(item.getShopId());
        request.setCategoryId(item.getCategory().getId());
        request.setTitle(title);
        request.setDescription(item.getDescription());
        request.setPrice(price);
        request.setSaleType(saleType);
        request.setStatus(status);
        return request;
    }

    @Test
    @DisplayName("교환 게시글로 올라간 상품은 삭제할 수 없다")
    void postedItemCannotBeDeleted() {
        openPost();

        assertThatThrownBy(() -> itemService.delete(1L, 1L))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("교환");
    }

    @Test
    @DisplayName("교환 게시글로 올라간 상품은 가격·상태를 바꿀 수 없지만 제목은 바꿀 수 있다")
    void postedItemKeepsPriceAndStatusButTitleCanChange() {
        openPost();
        Item item = itemRepository.findById(1L).orElseThrow();

        assertThatThrownBy(() -> itemService.update(1L, 1L, update(item.getPrice() + 1000, item.getStatus(), item.getSaleType(), item.getTitle())))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("교환");
        assertThatThrownBy(() -> itemService.update(1L, 1L, update(item.getPrice(), ItemStatus.CLOSED, item.getSaleType(), item.getTitle())))
            .isInstanceOf(BadRequestException.class);

        itemService.update(1L, 1L, update(item.getPrice(), item.getStatus(), item.getSaleType(), "제목만 수정"));
        assertThat(itemRepository.findById(1L).orElseThrow().getTitle()).isEqualTo("제목만 수정");
    }

    @Test
    @DisplayName("교환 게시글이 없는 상품은 자유롭게 수정·삭제할 수 있다")
    void itemWithoutPostIsFree() {
        itemService.delete(1L, 1L);

        assertThat(itemRepository.findById(1L).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("같은 상품으로 모집중 게시글이 있는지 조회된다")
    void repositoryFindsOpenPostForItem() {
        assertThat(exchangeItemRepository.existsByItem_IdAndStatusIn(1L, java.util.List.of(ExchangeItemStatus.OPEN, ExchangeItemStatus.MATCHED))).isFalse();

        openPost();

        assertThat(exchangeItemRepository.existsByItem_IdAndStatusIn(1L, java.util.List.of(ExchangeItemStatus.OPEN, ExchangeItemStatus.MATCHED))).isTrue();
    }
}
