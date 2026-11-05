package com.goodsbridge.exchange;

import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ExchangeFixVerificationTest {

    @Autowired
    private ExchangeItemRepository exchangeItemRepository;
    @Autowired
    private ExchangeRequestRepository exchangeRequestRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private EntityManager entityManager;

    // data.sql: item 1(seller=1), item 3(seller=2), shop 1(owner=1, APPROVED), user 1, user 2

    @Test
    @DisplayName("ExchangeItem 저장 후 flush+refresh로 createdAt이 null 없이 즉시 채워진다 (b528643 수정 전엔 null)")
    void exchangeItem_createdAt_notNull_afterSaveAndRefresh() {
        User user = userRepository.findById(1L).orElseThrow();
        Item item = itemRepository.findById(1L).orElseThrow();
        Shop shop = shopRepository.findById(1L).orElseThrow();

        ExchangeItem exchangeItem = ExchangeItem.builder()
            .user(user)
            .item(item)
            .shop(shop)
            .wantDescription("원하는 굿즈")
            .build();

        exchangeItemRepository.save(exchangeItem);
        exchangeItemRepository.flush();
        entityManager.refresh(exchangeItem);

        assertThat(exchangeItem.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("ExchangeRequest 생성 시 userAShowed/userBShowed가 false로 채워져 NOT NULL 제약을 위반하지 않는다 (수정 전엔 null로 insert 시도되어 제약 위반)")
    void exchangeRequest_showedFlags_defaultFalse_notNull() {
        User userA = userRepository.findById(1L).orElseThrow();
        Item itemA = itemRepository.findById(1L).orElseThrow();
        Shop shop = shopRepository.findById(1L).orElseThrow();
        Item itemB = itemRepository.findById(3L).orElseThrow(); // seller=2

        ExchangeItem exchangeItem = ExchangeItem.builder()
            .user(userA)
            .item(itemA)
            .shop(shop)
            .wantDescription("원하는 굿즈")
            .build();
        exchangeItemRepository.save(exchangeItem);

        ExchangeRequest exchangeRequest = ExchangeRequest.builder()
            .exchangeItemA(exchangeItem)
            .itemB(itemB)
            .message("교환 제안합니다")
            .paymentIdB("test-payment-id")
            .shop(shop)
            .depositAmountB(6000)
            .build();

        exchangeRequestRepository.save(exchangeRequest);
        exchangeRequestRepository.flush();
        entityManager.refresh(exchangeRequest);

        assertThat(exchangeRequest.getUserAShowed()).isFalse();
        assertThat(exchangeRequest.getUserBShowed()).isFalse();
        assertThat(exchangeRequest.getCreatedAt()).isNotNull();
    }
}
