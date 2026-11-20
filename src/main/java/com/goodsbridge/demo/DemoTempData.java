package com.goodsbridge.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodsbridge.item.Category;
import com.goodsbridge.item.CategoryJpaRepository;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.SaleType;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.shop.ShopStatus;
import com.goodsbridge.shop.ShopSubscription;
import com.goodsbridge.shop.ShopSubscriptionRepository;
import com.goodsbridge.shop.ShopSubscriptionStatus;
import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// 시연 화면들이 쓰는 임시 데이터: 실행마다 사용자·가게·상품을 새로 만들고, 끝나면 그 실행에서 만든 것과 거기 딸린 기록을 모두 지운다.
// 호출하는 쪽이 트랜잭션을 연다 (각 메서드는 저장만 한다).
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoTempData {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ShopSubscriptionRepository shopSubscriptionRepository;
    private final CategoryJpaRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    // 한 번의 시연 실행에서 만든 것들
    public static class Run {
        final String id = UUID.randomUUID().toString().substring(0, 8);
        final List<Long> userIds = new ArrayList<>();
        final List<Long> shopIds = new ArrayList<>();
        Long categoryId;

        // demo-pay- 로 시작하는 결제번호는 데모 결제 서비스가 PortOne 없이 통과시킨다
        public String paymentId(String suffix) {
            return "demo-pay-" + id + "-" + suffix;
        }
    }

    public Run start() {
        return new Run();
    }

    public User user(Run run, String suffix) {
        User user = userRepository.save(new User("tmp-" + run.id + "-" + suffix + "@demo-temp.demo", "demo",
            "시연" + run.id + suffix, LoginType.EMAIL, null));
        run.userIds.add(user.getId());
        return user;
    }

    // 입점 승인 + 구독 중인 가게 (구독 중이어야 이벤트·경매를 받는다)
    public Shop shop(Run run, User owner) {
        Shop shop = shopRepository.save(Shop.builder()
            .ownerUserId(owner.getId())
            .name("시연 가게 " + run.id)
            .region("서울")
            .address("시연용 임시 주소")
            .status(ShopStatus.APPROVED)
            .build());
        run.shopIds.add(shop.getId());
        shopSubscriptionRepository.save(ShopSubscription.builder()
            .shopId(shop.getId())
            .paymentId(run.paymentId("sub-" + shop.getId()))
            .setupFeePaid(true)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(1))
            .status(ShopSubscriptionStatus.ACTIVE)
            .build());
        return shop;
    }

    public Item item(Run run, User seller, SaleType saleType, int price, String title) {
        return item(run, seller, null, saleType, price, title);
    }

    // 가게 소속 상품 (가게 구독이 만료되면 판매 중지되는 대상)
    public Item item(Run run, User seller, Long shopId, SaleType saleType, int price, String title) {
        if (run.categoryId == null) {
            run.categoryId = categoryRepository.save(Category.builder().name("시연 " + run.id).build()).getId();
        }
        return itemRepository.save(Item.builder()
            .sellerUserId(seller.getId())
            .shopId(shopId)
            .category(categoryRepository.getReferenceById(run.categoryId))
            .title(title)
            .price(price)
            .saleType(saleType)
            .build());
    }

    // 요청 DTO 는 setter 가 없어서 JSON 으로 만든다
    public <T> T request(String json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // 외래키 순서대로 지운다. 실행에서 만든 사용자·가게에 딸린 기록(알림·결제·정산·입찰·교환·이벤트 참가)도 함께 지운다.
    // 한 트랜잭션으로 지워서 중간에 실패하면 반쯤 지워진 채 남지 않게 하고, 실패해도 기록만 남겨 시연 결과(또는 원래 오류)를 가리지 않는다.
    public void cleanup(Run run) {
        if (run.userIds.isEmpty()) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> deleteAll(run));
        } catch (RuntimeException e) {
            log.error("시연 임시 데이터 정리 실패 - run={}, users={}, shops={}", run.id, run.userIds, run.shopIds, e);
        }
    }

    private void deleteAll(Run run) {
        String users = in(run.userIds);
        String shops = run.shopIds.isEmpty() ? "(-1)" : in(run.shopIds);
        jdbcTemplate.update("delete from notifications_tb where receiver_id in " + users);
        jdbcTemplate.update("delete from settlement_history_tb where shop_id in " + shops);
        jdbcTemplate.update("delete from exchange_request_tb where shop_id in " + shops);
        jdbcTemplate.update("delete from exchange_item_tb where user_id in " + users);
        jdbcTemplate.update("delete from bid_tb where auction_id in (select id from auction_tb where shop_id in " + shops + ")");
        jdbcTemplate.update("delete from auction_tb where shop_id in " + shops);
        jdbcTemplate.update("delete from event_participation_tb where shop_event_id in (select id from shop_event_tb where shop_id in " + shops + ")");
        jdbcTemplate.update("delete from shop_event_tb where shop_id in " + shops);
        jdbcTemplate.update("delete from purchase_tb where buyer_user_id in " + users);
        jdbcTemplate.update("delete from payment_tb where user_id in " + users);
        jdbcTemplate.update("delete from item_tb where seller_user_id in " + users);
        if (run.categoryId != null) {
            jdbcTemplate.update("delete from category_tb where id = ?", run.categoryId);
        }
        jdbcTemplate.update("delete from shop_subscription_tb where shop_id in " + shops);
        jdbcTemplate.update("delete from shop_tb where id in " + shops);
        jdbcTemplate.update("delete from user_tb where id in " + users);
    }

    private static String in(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(",", "(", ")"));
    }
}
