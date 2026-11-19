package com.goodsbridge.exchange;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 탈퇴한 회원의 교환 게시글에는 제안할 수 없다 (받아 줄 사람이 없어 상대의 보증금과 상품이 묶이는 것을 막는다)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExchangeProposeWithdrawnOwnerTest {

    @Autowired ExchangeService exchangeService;
    @Autowired ExchangeItemRepository exchangeItemRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired UserRepository userRepository;
    @Autowired ShopRepository shopRepository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // data.sql: item 1(seller=1), shop 1(owner=1), user 1(일반), user 2(관리자) -> 제안자는 새로 만든 일반 회원
    @Test
    void 탈퇴한_회원의_게시글에는_제안할_수_없다() {
        User owner = userRepository.findById(1L).orElseThrow();
        ExchangeItem post = exchangeItemRepository.save(ExchangeItem.builder()
            .user(owner)
            .item(itemRepository.findById(1L).orElseThrow())
            .shop(shopRepository.findById(1L).orElseThrow())
            .wantDescription("원하는 굿즈")
            .build());
        User proposer = userRepository.save(User.builder().email("proposer@test.com").password("pw").nickname("제안자").build());
        userRepository.flush();
        jdbcTemplate.update("insert into item_tb (seller_user_id, shop_id, category_id, title, description, price, sale_type, status, paused_by_subscription, created_at) values (?, 1, 1, ?, ?, 1000, ?, ?, 0, now())",
            proposer.getId(), "제안 상품", "설명", "NORMAL", "ON_SALE");
        Long itemBId = jdbcTemplate.queryForObject("select id from item_tb where seller_user_id = ?", Long.class, proposer.getId());
        owner.withdraw();
        exchangeItemRepository.flush();

        assertThatThrownBy(() -> exchangeService.checkProposal(post.getId(), itemBId, proposer.getId()))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("탈퇴한 회원");
    }
}
