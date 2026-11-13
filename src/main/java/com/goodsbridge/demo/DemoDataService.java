package com.goodsbridge.demo;

import com.goodsbridge.exchange.ExchangeItem;
import com.goodsbridge.event.EventParticipation;
import com.goodsbridge.event.EventParticipationRepository;
import com.goodsbridge.event.ShopEvent;
import com.goodsbridge.event.ShopEventStatus;
import com.goodsbridge.event.ShopEventRepository;
import com.goodsbridge.exchange.ExchangeItemRepository;
import com.goodsbridge.exchange.ExchangeItemStatus;
import com.goodsbridge.exchange.ExchangeMessage;
import com.goodsbridge.exchange.ExchangeMessageRepository;
import com.goodsbridge.exchange.ExchangeRequest;
import com.goodsbridge.exchange.ExchangeRequestRepository;
import com.goodsbridge.exchange.ExchangeRequestStatus;
import com.goodsbridge.item.Category;
import com.goodsbridge.item.CategoryJpaRepository;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.item.SaleType;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.settlement.SettlementHistory;
import com.goodsbridge.settlement.SettlementHistoryRepository;
import com.goodsbridge.settlement.SettlementHistoryType;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.purchase.Purchase;
import com.goodsbridge.purchase.PurchaseRepository;
import com.goodsbridge.refund.Refund;
import com.goodsbridge.refund.RefundRepository;
import com.goodsbridge.refund.RefundType;
import com.goodsbridge.refund.RequestedBy;
import com.goodsbridge.refund.Status;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 시연용 계정(교환A/신청B/가게/관리자)과 시연 데이터를 만들고, 호출될 때마다 처음 상태로 되돌린다.
// 시연 데이터는 생성일을 먼 미래로 둬서 "최신순" 정렬에서 항상 맨 앞(카테고리별 2개)에 오게 한다.
@Service
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataService {

    static final String USER_A_EMAIL = "demo-user-a@goodsbridge.demo";
    static final String USER_B_EMAIL = "demo-user-b@goodsbridge.demo";
    static final String SHOP_EMAIL = "demo-shop@goodsbridge.demo";
    static final String SHOP_B_EMAIL = "demo-shop-b@goodsbridge.demo";
    static final String ADMIN_EMAIL = "demo-admin@goodsbridge.demo";

    private static final String FALLBACK_IMAGE = "/img/nav-shop.jpg";

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ShopSubscriptionRepository shopSubscriptionRepository;
    private final CategoryJpaRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final ExchangeItemRepository exchangeItemRepository;
    private final ShopEventRepository shopEventRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;
    private final PaymentRepository paymentRepository;
    private final PurchaseRepository purchaseRepository;
    private final RefundRepository refundRepository;
    private final SettlementHistoryRepository settlementHistoryRepository;
    private final ExchangeMessageRepository exchangeMessageRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void reset() {
        User userA = findOrCreateUser(USER_A_EMAIL, "데모교환A");
        User userB = findOrCreateUser(USER_B_EMAIL, "데모신청B");
        User shopOwner = findOrCreateUser(SHOP_EMAIL, "데모가게");
        User admin = findOrCreateUser(ADMIN_EMAIL, "데모관리자");
        // User 엔티티에 역할 변경 메서드가 없어서 데모 계정만 직접 갱신한다.
        jdbcTemplate.update("update user_tb set role = 'ADMIN' where id = ?", admin.getId());

        Shop shop = findOrCreateShop(shopOwner, "데모 굿즈샵", "서울", "서울시 마포구 데모로 1");
        resetSubscription(shop);

        // 가게B: 입점 승인만 받은 상태로 구독을 처음 시작하는 시연용 가게 (구독/상품 없음)
        User shopOwnerB = findOrCreateUser(SHOP_B_EMAIL, "데모가게B");
        Shop shopB = findOrCreateShop(shopOwnerB, "데모 굿즈샵B", "부산", "부산시 해운대구 데모로 2");
        jdbcTemplate.update("delete from shop_subscription_tb where shop_id = ?", shopB.getId());
        jdbcTemplate.update("delete from settlement_history_tb where shop_id = ?", shopB.getId());
        deleteDemoData(List.of(shopOwnerB.getId()));

        // 이전 시연(또는 테스트)에서 쌓인 데모 가게의 수수료 기록도 초기화해서 수익 요약이 시드 데이터와 맞게 한다
        jdbcTemplate.update("delete from settlement_history_tb where shop_id = ?", shop.getId());
        deleteDemoData(List.of(userA.getId(), userB.getId(), shopOwner.getId()));
        seed(userA, userB, shopOwner, shop);
        seedBranches();
    }

    private User findOrCreateUser(String email, String nickname) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.save(User.builder()
            .email(email)
            .password(passwordEncoder.encode("demo-" + System.nanoTime()))
            .nickname(nickname)
            .loginType(LoginType.EMAIL)
            .build()));
    }

    private Shop findOrCreateShop(User owner, String name, String region, String address) {
        var existing = shopRepository.findAllByOwnerUserIdAndDeletedFalse(owner.getId(), Pageable.ofSize(1));
        if (existing.isEmpty()) {
            return shopRepository.save(Shop.builder()
                .ownerUserId(owner.getId())
                .name(name)
                .region(region)
                .address(address)
                .status(ShopStatus.APPROVED)
                .build());
        }
        Shop shop = existing.getContent().get(0);
        // 시연 중 관리자 페르소나로 거절/폐업 처리했어도 처음 상태(승인)로 복구
        jdbcTemplate.update("update shop_tb set status = 'APPROVED' where id = ?", shop.getId());
        return shop;
    }

    // 구독 중인 가게만 상품을 등록할 수 있으므로, 데모 가게는 항상 1년짜리 활성 구독으로 되돌린다
    private void resetSubscription(Shop shop) {
        jdbcTemplate.update("delete from shop_subscription_tb where shop_id = ?", shop.getId());
        shopSubscriptionRepository.save(ShopSubscription.builder()
            .shopId(shop.getId())
            .paymentId("demo-subscription-" + UUID.randomUUID())
            .setupFeePaid(true)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusYears(1))
            .status(ShopSubscriptionStatus.ACTIVE)
            .build());
    }

    // 관리자 입점/구독 관리 시연: 체인점 지점들의 입점 신청 상태와 구독 현황 (로그인하지 않는 지점 주인 계정으로 만든다)
    // { 이름, 지역, 주소, 입점상태, 구독(NONE/ACTIVE/EXPIRING/EXPIRED), 신청 며칠 전 }
    private void seedBranches() {
        String[][] branches = {
            { "(테스트) 홍대점", "서울", "서울시 마포구 어울마당로 12", "PENDING", "NONE", "0" },
            { "(테스트) 강남점", "서울", "서울시 강남구 테헤란로 8길 21", "PENDING", "NONE", "1" },
            { "(테스트) 부산점", "부산", "부산시 해운대구 센텀로 45", "REJECTED", "NONE", "6" },
            { "(테스트) 성수점", "서울", "서울시 성동구 성수이로 77", "APPROVED", "ACTIVE", "40" },
            { "(테스트) 잠실점", "서울", "서울시 송파구 올림픽로 300", "APPROVED", "EXPIRING", "355" },
            { "(테스트) 대전점", "대전", "대전시 유성구 대학로 99", "APPROVED", "EXPIRED", "400" },
        };
        for (int n = 0; n < branches.length; n++) {
            String[] b = branches[n];
            User owner = findOrCreateUser("demo-branch-" + (n + 1) + "@goodsbridge.demo", "데모지점" + (n + 1));
            // 이전 시연에서 만든(또는 시연 중 바뀐) 지점은 지우고 처음 상태로 다시 만든다
            jdbcTemplate.update("delete from shop_subscription_tb where shop_id in (select id from shop_tb where owner_user_id = ?)", owner.getId());
            jdbcTemplate.update("delete from shop_tb where owner_user_id = ?", owner.getId());
            Shop branch = shopRepository.save(Shop.builder()
                .ownerUserId(owner.getId()).name(b[0]).region(b[1]).address(b[2])
                .status(ShopStatus.valueOf(b[3])).build());
            shopRepository.flush();
            jdbcTemplate.update("update shop_tb set created_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(Integer.parseInt(b[5]) > 0 ? Math.min(Integer.parseInt(b[5]), 30) : 0).minusHours(n + 1)), branch.getId());

            LocalDate start;
            LocalDate end;
            ShopSubscriptionStatus status = ShopSubscriptionStatus.ACTIVE;
            switch (b[4]) {
                case "ACTIVE" -> { start = LocalDate.now().minusDays(40); end = start.plusDays(365); }
                case "EXPIRING" -> { start = LocalDate.now().minusDays(355); end = LocalDate.now().plusDays(5); }
                case "EXPIRED" -> { start = LocalDate.now().minusDays(400); end = LocalDate.now().minusDays(10); status = ShopSubscriptionStatus.EXPIRED; }
                default -> { continue; }
            }
            shopSubscriptionRepository.save(ShopSubscription.builder()
                .shopId(branch.getId()).paymentId("demo-subscription-" + UUID.randomUUID())
                .setupFeePaid(true).startDate(start).endDate(end).status(status).build());
        }
    }

    // 시연 계정이 만든 상품/교환글과 거기에 딸린 데이터를 외래키 순서대로 지운다.
    private void deleteDemoData(List<Long> userIds) {
        String u = "(" + String.join(",", userIds.stream().map(String::valueOf).toList()) + ")";
        String items = "(select id from item_tb where seller_user_id in " + u + ")";
        String exchangeItems = "(select id from exchange_item_tb where user_id in " + u + " or item_id in " + items + ")";

        jdbcTemplate.update("delete from exchange_comment_tb where user_id in " + u + " or exchange_item_id in " + exchangeItems);
        jdbcTemplate.update("delete from exchange_message_tb where exchange_request_id in (select id from exchange_request_tb where exchange_itemaid in " + exchangeItems + " or itembid in " + items + ")");
        jdbcTemplate.update("delete from exchange_request_tb where exchange_itemaid in " + exchangeItems + " or itembid in " + items);
        jdbcTemplate.update("delete from exchange_item_tb where user_id in " + u + " or item_id in " + items);
        jdbcTemplate.update("delete from refund_tb where requester_user_id in " + u
            + " or payment_id in (select id from payment_tb where payment_id in (select payment_id from purchase_tb where item_id in " + items + "))");
        jdbcTemplate.update("delete from purchase_tb where item_id in " + items);
        jdbcTemplate.update("delete from bid_tb where auction_id in (select id from auction_tb where item_id in " + items + ")");
        jdbcTemplate.update("delete from auction_tb where item_id in " + items);
        jdbcTemplate.update("delete from event_participation_tb where shop_event_id in (select id from shop_event_tb where item_id in " + items + ")");
        jdbcTemplate.update("delete from shop_event_tb where item_id in " + items);
        jdbcTemplate.update("delete from item_tb where seller_user_id in " + u);
    }

    private void seed(User userA, User userB, User shopOwner, Shop shop) {
        List<Category> categories = categoryRepository.findAll();
        // 기존 시드 상품 사진(/images/seed_photo_*.jpg)을 돌려가며 써서 시연 상품마다 사진이 다르게 보이게 한다
        List<String> images = jdbcTemplate.queryForList(
            "select distinct image_url from item_tb where image_url like '/images/%' order by image_url", String.class);
        if (images.isEmpty()) images = List.of(FALLBACK_IMAGE);
        int[] cursor = {0};
        List<Long> pinnedItemIds = new ArrayList<>();
        List<Long> pinnedExchangeIds = new ArrayList<>();
        List<ExchangeItem> aPosts = new ArrayList<>();
        List<Item> shopItems = new ArrayList<>();

        for (Category category : categories) {
            for (int n = 1; n <= 2; n++) {
                // 가게(페르소나)가 파는 상품
                Item shopItem = itemRepository.save(Item.builder()
                    .sellerUserId(shopOwner.getId())
                    .shopId(shop.getId())
                    .category(category)
                    .title("(테스트) " + category.getName() + " 인기 상품 " + n)
                    .description("시연용 상품입니다. 가게 페르소나가 등록한 " + category.getName() + " 판매 상품 " + n + "번입니다.")
                    .price(n == 1 ? 15000 : 28000)
                    .saleType(SaleType.NORMAL)
                    .status(ItemStatus.ON_SALE)
                    .imageUrl(images.get(cursor[0]++ % images.size()))
                    .build());
                shopItems.add(shopItem);
                pinnedItemIds.add(shopItem.getId());

                // 교환A가 올린 교환글
                Item mine = itemRepository.save(Item.builder()
                    .sellerUserId(userA.getId())
                    .category(category)
                    .title("(테스트) " + category.getName() + " 교환해요 " + n)
                    .description("시연용 교환 상품입니다. 교환A 페르소나가 가진 " + category.getName() + " " + n + "번입니다.")
                    .price(n == 1 ? 12000 : 20000)
                    .saleType(SaleType.NORMAL)
                    .status(ItemStatus.ON_SALE)
                    .imageUrl(images.get(cursor[0]++ % images.size()))
                    .build());
                ExchangeItem post = exchangeItemRepository.save(ExchangeItem.builder()
                    .user(userA)
                    .item(mine)
                    .wantDescription("같은 " + category.getName() + " 종류로 교환 원해요")
                    .shop(shop)
                    .build());
                aPosts.add(post);
                pinnedExchangeIds.add(post.getId());
            }
        }

        // 신청B 보유 상품 20개 (교환글로는 올리지 않음). 카테고리를 돌아가며 배정
        List<Item> bItems = new ArrayList<>();
        if (!categories.isEmpty()) {
            for (int n = 1; n <= 20; n++) {
                bItems.add(itemRepository.save(Item.builder()
                    .sellerUserId(userB.getId())
                    .category(categories.get((n - 1) % categories.size()))
                    .title("(테스트) 신청B 보유 상품 " + n)
                    .description("시연용입니다. 신청B 페르소나가 교환 제안에 쓸 상품 " + n + "번입니다.")
                    .price(10000 + n * 2000)
                    .saleType(SaleType.NORMAL)
                    .status(ItemStatus.ON_SALE)
                    .imageUrl(images.get(cursor[0]++ % images.size()))
                    .build()));
            }
        }

        // 신청B가 교환A의 교환글에 미리 보낸 신청: {A 교환글 번호, B 상품 번호, 상태}
        // 대기 중/취소/거절/확정(만날 시간 미정·제안됨)/만남 확정/완료를 골고루 보여준다. 데모에서는 결제 검증을 건너뛴다.
        Object[][] scenarios = {
            {0, 1, "PENDING"}, {0, 2, "PENDING"}, {0, 3, "PENDING"}, {0, 4, "CANCELLED"}, {0, 5, "REJECTED"},
            {1, 6, "PENDING"}, {1, 7, "PENDING"},
            {2, 8, "CONFIRMED"}, {2, 9, "CANCELLED"},
            {3, 10, "CONFIRMED_PROPOSED"}, {3, 11, "CANCELLED"},
            {4, 12, "MEETING_CONFIRMED"}, {9, 18, "MEETING_CONFIRMED"},
            {5, 13, "COMPLETED"},
            {6, 14, "PENDING"}, {6, 15, "PENDING"},
            {7, 16, "NOSHOW_PARTIAL"},
            {8, 17, "NOSHOW_BOTH"},
        };
        if (!aPosts.isEmpty() && bItems.size() >= 20) {
            for (Object[] sc : scenarios) {
                seedRequest(aPosts.get((int) sc[0] % aPosts.size()), bItems.get((int) sc[1] - 1), userB, shop, (String) sc[2]);
            }
        }

        seedSales(shopItems, userB, shop);
        seedEvents(shopOwner, shop, categories, images, userA, userB);

        // 최신순 정렬에서 시연 데이터가 항상 맨 앞에 오도록 생성일을 먼 미래로 고정
        Timestamp pinned = Timestamp.valueOf(LocalDateTime.now().plusYears(10));
        pin("item_tb", pinnedItemIds, pinned);
        pin("exchange_item_tb", pinnedExchangeIds, pinned);
    }

    // 선착순 이벤트 5개: 이벤트 상품(EVENT)을 새로 등록하고 모집중 이벤트를 연다 (마감은 며칠 뒤)
    // 맨 앞은 쿠지(뽑기) 이벤트: 번호표 순서대로 매장에서 1회씩 뽑는 형태, 100개 한정 (1인 최대 10개)
    // 지우는 쪽은 deleteDemoData 가 가게 주인 상품에 걸린 이벤트/참가를 함께 정리한다
    private void seedEvents(User shopOwner, Shop shop, List<Category> categories, List<String> images, User userA, User userB) {
        if (categories.isEmpty()) return;
        // {제목, 상품 가격, 정원, 참가비, 마감(일 뒤), 이미지(null 이면 시드 사진), 설명, 1인당 최대 수량}
        Object[][] events = {
            {"(테스트) D-Series 스페셜 쿠지 RAFFLE FESTIVAL 2.0", 17000, 100, 5000, 5, "/img/nav-event.png",
                "A상 스케일 피규어 · B상 아크릴 스탠드 · C상 캔뱃지 · 라스트원상까지, 모든 회차에 상품이 나와요.", 10},
            {"(테스트) 한정판 포토카드 세트 오픈런", 30000, 5, 10000, 3, "/img/event-plush-short.jpg",
                "시연용 선착순 이벤트 상품입니다. 참가비를 내면 번호표가 발급돼요.", 1},
            {"(테스트) 시즌 그리팅 선착순 예약", 45000, 3, 5000, 1, "/img/event-plush-long.jpg",
                "시연용 선착순 이벤트 상품입니다. 참가비를 내면 번호표가 발급돼요.", 1},
            {"(테스트) 만화 컷 아트 포스터 선착순 오픈", 25000, 10, 3000, 4, "/img/event-manga.jpg",
                "시연용 선착순 이벤트 상품입니다. 참가비를 내면 번호표가 발급돼요.", 2},
            {"(테스트) 스탠딩 피규어 한정판 선착순 예약", 89000, 20, 9000, 6, "/img/event-figure.jpg",
                "시연용 선착순 이벤트 상품입니다. 참가비를 내면 번호표가 발급돼요.", 3},
        };
        List<ShopEvent> open = new ArrayList<>();
        for (int i = 0; i < events.length; i++) {
            Object[] ev = events[i];
            Item item = saveEventItem(shopOwner, shop, categories.get(i % categories.size()), (String) ev[0], (String) ev[6],
                (Integer) ev[1], ev[5] != null ? (String) ev[5] : images.get(i % images.size()));
            open.add(shopEventRepository.save(ShopEvent.builder()
                .shop(shop)
                .item(item)
                .capacity((Integer) ev[2])
                .feeAmount((Integer) ev[3])
                .maxPerUser((Integer) ev[7])
                .deadline(LocalDateTime.now().plusDays((Integer) ev[4]))
                .build()));
        }

        // ----- 참가 내역 (모집중 이벤트): 같은 번호표에 구매가 여러 건 쌓인 경우, 내가 취소한 건
        ShopEvent kuji = open.get(0), manga = open.get(3), figure = open.get(4);
        seedParticipation(kuji, userB, 1, 3, KEEP);
        seedParticipation(kuji, userB, 1, 2, KEEP);   // 추가 구매 (같은 번호표 #1)
        seedParticipation(kuji, userA, 2, 4, KEEP);
        seedParticipation(manga, userB, 1, 1, KEEP);
        seedParticipation(figure, userB, 1, 2, KEEP);
        seedParticipation(figure, userB, 2, 1, EventParticipation::cancel);        // 내가 취소한 구매

        // ----- 모집이 끝난 이벤트들 (마이페이지 '방문 대기' / '종료된 이벤트' / '취소된 이벤트' 확인용)
        // 모집 마감, 방문 날짜가 오늘: 가게의 '오늘 방문' 분류 / 손님의 방문 대기
        ShopEvent waiting = savePastEvent(shopOwner, shop, categories.get(0), "(테스트) 모집 마감 · 방문 대기 이벤트", 25000, 10, 5000, 2,
            LocalDateTime.now().minusHours(3), LocalDate.now(), "/img/event-plush-short.jpg", ShopEventStatus.CLOSED);
        seedParticipation(waiting, userB, 1, 2, KEEP);
        seedParticipation(waiting, userA, 2, 1, KEEP);
        // 방문 날짜까지 지난 이벤트: 종료
        ShopEvent ended = savePastEvent(shopOwner, shop, categories.get(1 % categories.size()), "(테스트) 지난 주 종료된 이벤트", 18000, 10, 5000, 2,
            LocalDateTime.now().minusDays(8), LocalDate.now().minusDays(1), "/img/event-plush-long.jpg", ShopEventStatus.CLOSED);
        seedParticipation(ended, userB, 1, 1, KEEP);
        seedParticipation(ended, userA, 2, 2, KEEP);
        // 지난 달에 끝난 이벤트: 정산 달력의 '저번 달' 확인용
        ShopEvent lastMonth = savePastEvent(shopOwner, shop, categories.get(3 % categories.size()), "(테스트) 지난 달 종료된 이벤트", 22000, 10, 5000, 2,
            LocalDateTime.now().minusDays(35), LocalDate.now().minusDays(28), "/img/event-figure.jpg", ShopEventStatus.CLOSED);
        seedParticipation(lastMonth, userB, 1, 2, KEEP);
        seedParticipation(lastMonth, userA, 2, 3, KEEP);
        // 가게가 취소한 이벤트: 참가비 환불
        ShopEvent cancelled = savePastEvent(shopOwner, shop, categories.get(2 % categories.size()), "(테스트) 가게 사정으로 취소된 이벤트", 30000, 10, 6000, 2,
            LocalDateTime.now().plusDays(2), LocalDate.now().plusDays(6), "/img/event-manga.jpg", ShopEventStatus.CANCELLED);
        seedParticipation(cancelled, userB, 1, 2, EventParticipation::refund);
        seedParticipation(cancelled, userA, 2, 1, EventParticipation::refund);
    }

    private Item saveEventItem(User shopOwner, Shop shop, Category category, String title, String description, int price, String imageUrl) {
        return itemRepository.save(Item.builder()
            .sellerUserId(shopOwner.getId())
            .shopId(shop.getId())
            .category(category)
            .title(title)
            .description(description)
            .price(price)
            .saleType(SaleType.EVENT)
            .status(ItemStatus.ON_SALE)
            .imageUrl(imageUrl)
            .build());
    }

    // 마감/취소된 이벤트 (정산은 끝난 것으로 표시해서 스케줄러가 시연 데이터를 건드리지 않게 한다)
    private ShopEvent savePastEvent(User shopOwner, Shop shop, Category category, String title, int price, int capacity, int fee, int maxPerUser,
                                    LocalDateTime deadline, LocalDate visitUntil, String imageUrl, ShopEventStatus status) {
        Item item = saveEventItem(shopOwner, shop, category, title, "시연용 이벤트 내역 데이터입니다.", price, imageUrl);
        ShopEvent event = ShopEvent.builder()
            .shop(shop).item(item).capacity(capacity).feeAmount(fee).maxPerUser(maxPerUser)
            .deadline(deadline).visitUntil(visitUntil)
            .build();
        event.updateStatus(status);
        event.markSettled();
        return shopEventRepository.save(event);
    }

    private static final java.util.function.Consumer<EventParticipation> KEEP = p -> { };

    // 이벤트 참가 한 건: 결제 행을 만들고, 가게 취소(환불)/내가 취소한 건이면 그 상태로 바꾼다.
    // 수량(currentCount)은 참가중인 건만 센다.
    private void seedParticipation(ShopEvent event, User user, int ticket, int quantity, java.util.function.Consumer<EventParticipation> finish) {
        int amount = event.getFeeAmount() * quantity;
        Payment payment = paymentRepository.save(demoPayment(user, amount, PaymentPurpose.EVENT_FEE));
        EventParticipation participation = EventParticipation.builder()
            .shopEvent(event).user(user).paymentId(payment.getPaymentId()).ticketNumber(ticket).quantity(quantity)
            .build();
        finish.accept(participation);
        if (participation.isJoined()) {
            event.increaseCurrentCount(quantity);
        } else {
            payment.refund(amount);   // 취소/환불된 건은 참가비를 돌려준 결제로 남긴다
        }
        eventParticipationRepository.save(participation);
        // 마감 정산이 끝난 이벤트의 참가비는 가게 정산(EVENT_FEE)으로도 기록해 둔다 (정산 시각은 마감 직후)
        if (event.isSettled() && participation.isJoined()) {
            recordFee(event.getShop(), payment, amount, SettlementHistoryType.EVENT_FEE);
            settlementHistoryRepository.flush();
            jdbcTemplate.update("update settlement_history_tb set created_at = ? where payment_id = ? and type = 'EVENT_FEE'",
                Timestamp.valueOf(event.getDeadline().plusMinutes(1)), payment.getId());
        }
    }

    private void seedRequest(ExchangeItem post, Item bItem, User userB, Shop shop, String state) {
        int depositB = Math.max(Math.round(bItem.getPrice() * 0.2f), 2000);
        // 환불/자동취소 시 처리할 수 있도록 DemoPaymentService 와 같은 접두사로 가짜 결제 행을 함께 만든다
        Payment paymentB = paymentRepository.save(demoPayment(userB, depositB));
        ExchangeRequest request = exchangeRequestRepository.save(ExchangeRequest.builder()
            .exchangeItemA(post)
            .itemB(bItem)
            .message("교환해요")
            .paymentIdB(paymentB.getPaymentId())
            .shop(shop)
            .depositAmountB(depositB)
            .build());

        switch (state) {
            case "CANCELLED", "REJECTED" -> {
                paymentB.refund(depositB);
                request.updateStatus(state.equals("CANCELLED") ? ExchangeRequestStatus.CANCELLED : ExchangeRequestStatus.REJECTED);
            }
            case "PENDING" -> { }
            default -> { // 확정 이후 단계: 교환A도 보증금을 냈다
                int depositA = Math.max(Math.round(post.getItem().getPrice() * 0.2f), 2000);
                request.updateDepositAmountA(depositA);
                Payment paymentA = paymentRepository.save(demoPayment(post.getUser(), depositA));
                request.updatePaymentIdA(paymentA.getPaymentId());
                request.updateStatus(ExchangeRequestStatus.CONFIRMED);
                request.updateDeadlineForVisit();
                // 수락 이후 두 사람이 나눈 대화 (완료/불참 교환의 '지난 대화'에서도 보인다)
                String[] lines = {"수락했어요! 가게에서 언제 만날까요?", "감사합니다. 이번 주말 오후는 어떠세요?", "좋아요, 그때 가게에서 뵐게요."};
                for (int i = 0; i < lines.length; i++) {
                    exchangeMessageRepository.save(ExchangeMessage.builder()
                        .exchangeRequest(request)
                        .senderUserId(i % 2 == 0 ? post.getUser().getId() : userB.getId())
                        .content(lines[i])
                        .build());
                }
                post.updateStatus(ExchangeItemStatus.MATCHED);
                post.getItem().updateStatus(ItemStatus.SOLD);
                bItem.updateStatus(ItemStatus.SOLD);
                if (!state.equals("CONFIRMED")) {
                    // 만남 확정 건은 '오늘 방문 예정'에 보이도록 오늘(약 1시간 반~4시간 뒤)로, 나머지는 이틀 뒤로 잡는다
                    LocalDateTime meeting = LocalDateTime.now().plusDays(2);
                    if (state.equals("MEETING_CONFIRMED")) {
                        meeting = LocalDateTime.now().plusMinutes(bItem.getId() % 2 == 0 ? 90 : 240);
                        if (!meeting.toLocalDate().equals(java.time.LocalDate.now())) meeting = java.time.LocalDate.now().atTime(23, 30);
                    }
                    request.proposeMeeting(post.getUser().getId(), meeting);
                }
                if (state.equals("MEETING_CONFIRMED") || state.equals("COMPLETED") || state.startsWith("NOSHOW")) {
                    request.confirmMeeting();
                }
                if (state.equals("COMPLETED")) {
                    request.checkInA(true);
                    request.checkInB(true);
                    request.updateStatus(ExchangeRequestStatus.COMPLETED);
                    int refundA = Math.round(depositA * 0.75f), refundB = Math.round(depositB * 0.75f);
                    paymentA.refund(refundA);
                    paymentB.refund(refundB);
                    recordFee(shop, paymentA, depositA - refundA, SettlementHistoryType.EXCHANGE_FEE);
                    recordFee(shop, paymentB, depositB - refundB, SettlementHistoryType.EXCHANGE_FEE);
                } else if (state.equals("NOSHOW_PARTIAL")) {
                    request.checkInA(true);
                    request.updateStatus(ExchangeRequestStatus.EXPIRED_PARTIAL);
                    paymentA.refund(depositA); // 온 사람(A)만 전액 환불, 안 온 사람(B)은 보증금 소멸
                    recordFee(shop, paymentB, depositB, SettlementHistoryType.NO_SHOW_FEE);
                } else if (state.equals("NOSHOW_BOTH")) {
                    request.updateStatus(ExchangeRequestStatus.EXPIRED_BOTH);
                    int halfA = Math.round(depositA * 0.5f), halfB = Math.round(depositB * 0.5f);
                    paymentA.refund(halfA);
                    paymentB.refund(halfB);
                    recordFee(shop, paymentA, depositA - halfA, SettlementHistoryType.NO_SHOW_FEE);
                    recordFee(shop, paymentB, depositB - halfB, SettlementHistoryType.NO_SHOW_FEE);
                }
            }
        }
    }

    // 서버 정산(ExchangeService.settle)과 같이 가게가 가져간 수수료를 기록
    // 가게 판매 상품 관리 시연: 신청B가 가게 상품을 구매한 내역 (배송 단계별 + 환불 요청/부분환불/거절)
    // 실제 PortOne 결제 없이 결제/구매/환불 기록만 만든다
    private void seedSales(List<Item> shopItems, User buyer, Shop shop) {
        // 뒤의 두 건은 판매 정산(오늘/이번 달) 시연용 구매 확정 건
        String[] scenarios = { "PREPARING", "SHIPPED", "CONFIRMED", "PARTIAL_REFUND", "REFUND_REQUESTED", "REFUND_REJECTED", "CONFIRMED", "CONFIRMED", "DELIVERED" };
        int[] monthsAgo = { 0, 0, 1, 1, 0, 2, 0, 0, 0 };
        int[] daysAgo = { 0, 0, 0, 0, 0, 0, 1, 7, 1 };
        String[] addresses = {
            "서울시 마포구 와우산로 21, 3층", "부산시 해운대구 해운대로 570, 1203호", "대구시 중구 동성로 12",
            "인천시 연수구 송도과학로 32, 701호", "광주시 서구 상무중앙로 7", "대전시 유성구 대학로 99, 202호",
            "수원시 영통구 광교중앙로 140, 905호", "제주시 연동 신대로 20", "울산시 남구 삼산로 55, 1001호"
        };
        // 카테고리별로 두 번째 상품(28,000원)을 골라 판매완료로 만든다 (첫 번째는 판매중으로 남긴다)
        List<Item> targets = new ArrayList<>();
        for (int i = 1; i < shopItems.size() && targets.size() < scenarios.length; i += 2) targets.add(shopItems.get(i));

        for (int n = 0; n < targets.size(); n++) {
            Item item = targets.get(n);
            String state = scenarios[n];
            Payment payment = paymentRepository.save(Payment.builder()
                .paymentId("demo-sale-" + UUID.randomUUID())
                .pgTxId("demo")
                .user(buyer)
                .amount(item.getPrice())
                .purpose(PaymentPurpose.PURCHASE)
                .build());
            Purchase purchase = Purchase.builder()
                .item(item).user(buyer).paymentId(payment.getPaymentId()).deliveryAddress(addresses[n])
                .build();
            if (!state.equals("PREPARING")) purchase.ship("CJ" + (500100200300L + n));
            // 지난달 이전 주문(구매 확정/부분 환불/환불 거절)은 이미 배송이 끝난 상태
            if (state.equals("CONFIRMED") || state.equals("PARTIAL_REFUND") || state.equals("REFUND_REJECTED") || state.equals("DELIVERED")) purchase.markDelivered();
            if (state.equals("CONFIRMED")) purchase.confirm();
            purchaseRepository.save(purchase);
            item.updateStatus(ItemStatus.SOLD);

            switch (state) {
                case "PARTIAL_REFUND" -> {
                    Refund refund = demoRefund(payment, buyer, 5000, "구성품 일부 누락 (부분 환불)", RequestedBy.USER, Status.REQUESTED);
                    refund.approve();
                    payment.refund(5000);
                }
                case "REFUND_REQUESTED" -> demoRefund(payment, buyer, item.getPrice(), "단순 변심으로 환불 요청합니다", RequestedBy.USER, Status.REQUESTED);
                // 상품을 받은 구매자가 포장 파손으로 일부 환불을 요청했지만, 상품 자체는 이상이 없어 거절된 경우
                case "REFUND_REJECTED" -> demoRefund(payment, buyer, 3000, "받은 상품 포장 박스가 찢어져 있어요", RequestedBy.USER, Status.REQUESTED)
                    .reject("보내주신 사진 확인 결과 상품 자체에는 손상이 없어 거절합니다");
                default -> { }
            }

            // 기간 필터 시연용으로 구매일/환불 요청일을 이번 달·저번 달·두 달 전으로 나눈다
            // (환불 승인/거절 변경이 나중에 flush 되며 날짜를 덮어쓰지 않도록 먼저 flush)
            refundRepository.flush();
            LocalDateTime purchasedAt = LocalDateTime.now().minusMonths(monthsAgo[n]).minusDays(daysAgo[n]).minusHours(n + 1);
            LocalDateTime refundAt = monthsAgo[n] == 0 ? LocalDateTime.now().minusMinutes(30) : purchasedAt.plusHours(20);
            jdbcTemplate.update("update purchase_tb set created_at = ? where payment_id = ?", Timestamp.valueOf(purchasedAt), payment.getPaymentId());
            jdbcTemplate.update("update refund_tb set created_at = ? where payment_id = ?", Timestamp.valueOf(refundAt), payment.getId());

            // 구매 확정되면 판매 금액이 가게 정산(PURCHASE_FEE)으로 기록된다 (PurchaseService.confirm 과 같은 금액)
            if (state.equals("CONFIRMED")) {
                recordFee(shop, payment, item.getPrice(), SettlementHistoryType.PURCHASE_FEE);
                settlementHistoryRepository.flush();
                LocalDateTime settledAt = purchasedAt.plusDays(2).isAfter(LocalDateTime.now()) ? LocalDateTime.now().minusMinutes(20) : purchasedAt.plusDays(2);
                jdbcTemplate.update("update settlement_history_tb set created_at = ? where payment_id = ? and type = 'PURCHASE_FEE'",
                    Timestamp.valueOf(settledAt), payment.getId());
            }
        }
    }

    private Refund demoRefund(Payment payment, User requester, int amount, String reason, RequestedBy by, Status status) {
        return refundRepository.save(Refund.builder()
            .payment(payment).user(requester).reason(reason).requestedAmount(amount)
            .status(status).requestedBy(by).type(RefundType.PURCHASE)
            .build());
    }

    private void recordFee(Shop shop, Payment payment, int fee, SettlementHistoryType type) {
        settlementHistoryRepository.save(new SettlementHistory(shop, payment, fee, type));
    }

    private Payment demoPayment(User user, int amount) {
        return demoPayment(user, amount, PaymentPurpose.EXCHANGE_DEPOSIT);
    }

    private Payment demoPayment(User user, int amount, PaymentPurpose purpose) {
        return Payment.builder()
            .paymentId("demo-pay-" + UUID.randomUUID())
            .pgTxId("demo")
            .user(user)
            .amount(amount)
            .purpose(purpose)
            .build();
    }

    private void pin(String table, List<Long> ids, Timestamp pinned) {
        if (ids.isEmpty()) return;
        String in = String.join(",", ids.stream().map(String::valueOf).toList());
        jdbcTemplate.update("update " + table + " set created_at = ? where id in (" + in + ")", pinned);
    }
}
