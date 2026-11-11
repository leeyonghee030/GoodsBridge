package com.goodsbridge.demo;

import com.goodsbridge.exchange.ExchangeItem;
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
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.shop.ShopStatus;
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
    static final String ADMIN_EMAIL = "demo-admin@goodsbridge.demo";

    private static final String FALLBACK_IMAGE = "/img/nav-shop.jpg";

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final CategoryJpaRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final ExchangeItemRepository exchangeItemRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;
    private final PaymentRepository paymentRepository;
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

        Shop shop = findOrCreateShop(shopOwner);

        // 이전 시연(또는 테스트)에서 쌓인 데모 가게의 수수료 기록도 초기화해서 수익 요약이 시드 데이터와 맞게 한다
        jdbcTemplate.update("delete from settlement_history_tb where shop_id = ?", shop.getId());
        deleteDemoData(List.of(userA.getId(), userB.getId(), shopOwner.getId()));
        seed(userA, userB, shopOwner, shop);
    }

    private User findOrCreateUser(String email, String nickname) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.save(User.builder()
            .email(email)
            .password(passwordEncoder.encode("demo-" + System.nanoTime()))
            .nickname(nickname)
            .loginType(LoginType.EMAIL)
            .build()));
    }

    private Shop findOrCreateShop(User owner) {
        var existing = shopRepository.findAllByOwnerUserIdAndDeletedFalse(owner.getId(), Pageable.ofSize(1));
        if (existing.isEmpty()) {
            return shopRepository.save(Shop.builder()
                .ownerUserId(owner.getId())
                .name("데모 굿즈샵")
                .region("서울")
                .address("서울시 마포구 데모로 1")
                .status(ShopStatus.APPROVED)
                .build());
        }
        Shop shop = existing.getContent().get(0);
        // 시연 중 관리자 페르소나로 거절/폐업 처리했어도 처음 상태(승인)로 복구
        jdbcTemplate.update("update shop_tb set status = 'APPROVED' where id = ?", shop.getId());
        return shop;
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

        for (Category category : categories) {
            for (int n = 1; n <= 2; n++) {
                // 가게(페르소나)가 파는 상품
                pinnedItemIds.add(itemRepository.save(Item.builder()
                    .sellerUserId(shopOwner.getId())
                    .shopId(shop.getId())
                    .category(category)
                    .title("(테스트) " + category.getName() + " 인기 상품 " + n)
                    .description("시연용 상품입니다. 가게 페르소나가 등록한 " + category.getName() + " 판매 상품 " + n + "번입니다.")
                    .price(n == 1 ? 15000 : 28000)
                    .saleType(SaleType.NORMAL)
                    .status(ItemStatus.ON_SALE)
                    .imageUrl(images.get(cursor[0]++ % images.size()))
                    .build()).getId());

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

        // 최신순 정렬에서 시연 데이터가 항상 맨 앞에 오도록 생성일을 먼 미래로 고정
        Timestamp pinned = Timestamp.valueOf(LocalDateTime.now().plusYears(10));
        pin("item_tb", pinnedItemIds, pinned);
        pin("exchange_item_tb", pinnedExchangeIds, pinned);
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
    private void recordFee(Shop shop, Payment payment, int fee, SettlementHistoryType type) {
        settlementHistoryRepository.save(new SettlementHistory(shop, payment, fee, type));
    }

    private Payment demoPayment(User user, int amount) {
        return Payment.builder()
            .paymentId("demo-pay-" + UUID.randomUUID())
            .pgTxId("demo")
            .user(user)
            .amount(amount)
            .purpose(PaymentPurpose.EXCHANGE_DEPOSIT)
            .build();
    }

    private void pin(String table, List<Long> ids, Timestamp pinned) {
        if (ids.isEmpty()) return;
        String in = String.join(",", ids.stream().map(String::valueOf).toList());
        jdbcTemplate.update("update " + table + " set created_at = ? where id in (" + in + ")", pinned);
    }
}
