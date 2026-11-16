package com.goodsbridge.exchange;

import com.goodsbridge.exchange.dto.ExchangeItemResponse;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
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

/**
 * 교환 게시글 제목과 올린 상품 이름은 따로 관리된다.
 * 테스트 데이터: 상품 1(주인 사용자 1), 가게 1(승인됨).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExchangeItemTitleTest {
    @Autowired ExchangeService exchangeService;
    @Autowired ExchangeItemRepository exchangeItemRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired UserRepository userRepository;
    @Autowired ShopRepository shopRepository;

    private ExchangeItem post(String title) {
        User user = userRepository.findById(1L).orElseThrow();
        Item item = itemRepository.findById(1L).orElseThrow();
        Shop shop = shopRepository.findById(1L).orElseThrow();
        return exchangeItemRepository.save(ExchangeItem.builder().user(user).item(item).shop(shop)
            .wantDescription("원하는 굿즈").title(title).build());
    }

    @Test
    @DisplayName("제목을 따로 적으면 게시글 제목으로 보이고 상품 이름은 그대로다")
    void ownTitleIsShownAndItemNameIsKept() {
        ExchangeItem post = post("급처! 피규어 교환해요");

        ExchangeItemResponse response = ExchangeItemResponse.from(post);

        assertThat(response.getTitle()).isEqualTo("급처! 피규어 교환해요");
        assertThat(response.getItemTitle()).isEqualTo(post.getItem().getTitle());
    }

    @Test
    @DisplayName("제목을 비우면 상품 이름이 제목으로 보인다")
    void blankTitleFallsBackToItemName() {
        ExchangeItem post = post("   ");

        assertThat(ExchangeItemResponse.from(post).getTitle()).isEqualTo(post.getItem().getTitle());
    }

    @Test
    @DisplayName("게시글 제목을 수정해도 상품 이름은 바뀌지 않고, 비우면 상품 이름으로 돌아간다")
    void updatingTitleDoesNotRenameTheItem() {
        ExchangeItem post = post(null);
        String itemName = post.getItem().getTitle();

        ExchangeItemResponse changed = exchangeService.updateExchangeItem(post.getId(), 1L, "새 게시글 제목", "원하는 굿즈", 1L);
        assertThat(changed.getTitle()).isEqualTo("새 게시글 제목");
        assertThat(changed.getItemTitle()).isEqualTo(itemName);
        assertThat(itemRepository.findById(1L).orElseThrow().getTitle()).isEqualTo(itemName);

        ExchangeItemResponse cleared = exchangeService.updateExchangeItem(post.getId(), 1L, "", "원하는 굿즈", 1L);
        assertThat(cleared.getTitle()).isEqualTo(itemName);
    }
}
