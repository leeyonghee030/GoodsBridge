package com.goodsbridge.demo;

import com.goodsbridge.exchange.ExchangeItem;
import com.goodsbridge.auction.Auction;
import com.goodsbridge.board.Board;
import com.goodsbridge.board.BoardRepository;
import com.goodsbridge.board.BoardType;
import com.goodsbridge.board.InquiryTargetType;
import com.goodsbridge.board.Reply;
import com.goodsbridge.auction.AuctionRepository;
import com.goodsbridge.auction.Bid;
import com.goodsbridge.auction.BidRepository;
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
import com.goodsbridge.purchase.PurchaseSource;
import com.goodsbridge.purchase.PurchaseStatus;
import com.goodsbridge.purchase.PurchaseRepository;
import com.goodsbridge.notification.Notification;
import com.goodsbridge.notification.NotificationRepository;
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

    // 시연 데이터는 어느 컴퓨터에서 만들어도 똑같이 나오게, 카테고리와 사진을 이름으로 고정한다.
    // (카테고리는 API 로만 만들어져서 컴퓨터마다 DB 에 있는 카테고리가 다르면 시연 상품이 달라졌다)
    private static final List<String> DEMO_CATEGORIES = List.of(
        "포토카드", "앨범", "피규어", "응원봉", "포스터", "스티커", "키링", "포카홀더", "엽서", "포토북", "인형", "패션");
    // 시연 상품 사진: 저장소의 static/img/demo 에 들어 있다 (업로드 폴더는 git 에 없어서 다른 컴퓨터에는 없다)
    private static final List<String> DEMO_PHOTOS = List.of(
        "/img/demo/seed_photo_1.jpg", "/img/demo/seed_photo_11.jpg", "/img/demo/seed_photo_12.jpg", "/img/demo/seed_photo_13.jpg", "/img/demo/seed_photo_14.jpg", "/img/demo/seed_photo_15.jpg", "/img/demo/seed_photo_16.jpg", "/img/demo/seed_photo_19.jpg", "/img/demo/seed_photo_2.jpg", "/img/demo/seed_photo_20.jpg", "/img/demo/seed_photo_21.jpg", "/img/demo/seed_photo_22.jpg", "/img/demo/seed_photo_23.jpg", "/img/demo/seed_photo_24.jpg", "/img/demo/seed_photo_27.jpg", "/img/demo/seed_photo_28.jpg", "/img/demo/seed_photo_29.jpg", "/img/demo/seed_photo_30.jpg", "/img/demo/seed_photo_34.jpg", "/img/demo/seed_photo_35.jpg", "/img/demo/seed_photo_36.jpg", "/img/demo/seed_photo_37.jpg", "/img/demo/seed_photo_38.jpg", "/img/demo/seed_photo_39.jpg", "/img/demo/seed_photo_40.jpg", "/img/demo/seed_photo_41.jpg", "/img/demo/seed_photo_42.jpg", "/img/demo/seed_photo_43.jpg", "/img/demo/seed_photo_44.jpg", "/img/demo/seed_photo_45.jpg", "/img/demo/seed_photo_47.jpg", "/img/demo/seed_photo_48.jpg", "/img/demo/seed_photo_51.jpg", "/img/demo/seed_photo_53.jpg", "/img/demo/seed_photo_7.jpg", "/img/demo/seed_photo_9.jpg");

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ShopSubscriptionRepository shopSubscriptionRepository;
    private final CategoryJpaRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final ExchangeItemRepository exchangeItemRepository;
    private final ShopEventRepository shopEventRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;
    private final PaymentRepository paymentRepository;
    private final PurchaseRepository purchaseRepository;
    private final RefundRepository refundRepository;
    private final NotificationRepository notificationRepository;
    private final BoardRepository boardRepository;
    private final SettlementHistoryRepository settlementHistoryRepository;
    private final ExchangeMessageRepository exchangeMessageRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final DemoSampleDataService demoSampleDataService;

    @Transactional
    public void reset() {
        User userA = findOrCreateUser(USER_A_EMAIL, "데모교환A");
        User userB = findOrCreateUser(USER_B_EMAIL, "데모신청B");
        User shopOwner = findOrCreateUser(SHOP_EMAIL, "데모가게");
        User admin = findOrCreateUser(ADMIN_EMAIL, "데모관리자");
        // User 엔티티에 역할 변경 메서드가 없어서 데모 계정만 직접 갱신한다.
        jdbcTemplate.update("update user_tb set role = 'ADMIN' where id = ?", admin.getId());
        // 구매·낙찰 잔금 결제의 "기본 주소"를 누구나 같은 화면으로 시연할 수 있게 데모 구매자 계정에 기본 주소를 넣는다
        // (다른 데모 계정 설정처럼 SQL 로 바로 바꾼다. 엔티티를 고치면 커밋 때 행 전체가 저장돼 아래 상태 복구 SQL 을 덮을 수 있다)
        String demoAddress = "update user_tb set postal_code = ?, address = ?, detail_address = ? where id = ?";
        jdbcTemplate.update(demoAddress, "04044", "서울 마포구 양화로 45 (서교동)", "101동 1201호", userA.getId());
        jdbcTemplate.update(demoAddress, "04420", "서울 용산구 유엔빌리지길 1 (한남동)", "12층", userB.getId());

        Shop shop = findOrCreateShop(shopOwner, "데모 굿즈샵", "서울", "서울시 마포구 데모로 1");
        resetSubscription(shop);

        // 가게B: 입점 승인만 받은 상태로 구독을 처음 시작하는 시연용 가게 (구독/상품 없음)
        User shopOwnerB = findOrCreateUser(SHOP_B_EMAIL, "데모가게B");
        // 이메일이 익명화되기 전에 탈퇴해서 이메일이 그대로 남은 옛 데모 계정은 초기화할 때 되살린다 (User 엔티티에 복구 메서드가 없어 직접 갱신).
        // 지금은 탈퇴하면 이메일이 바뀌어 위 findOrCreateUser 가 새 데모 계정을 만들므로 이 갱신은 옛 데이터에만 해당한다.
        jdbcTemplate.update("update user_tb set status = 'ACTIVE' where email in (?, ?, ?, ?, ?)",
            USER_A_EMAIL, USER_B_EMAIL, SHOP_EMAIL, SHOP_B_EMAIL, ADMIN_EMAIL);
        Shop shopB = findOrCreateShop(shopOwnerB, "데모 굿즈샵B", "부산", "부산시 해운대구 데모로 2");
        jdbcTemplate.update("delete from shop_subscription_tb where shop_id = ?", shopB.getId());
        jdbcTemplate.update("delete from settlement_history_tb where shop_id = ?", shopB.getId());
        deleteDemoData(List.of(shopOwnerB.getId()));

        // 이전 시연(또는 테스트)에서 쌓인 데모 가게의 수수료 기록도 초기화해서 수익 요약이 시드 데이터와 맞게 한다
        jdbcTemplate.update("delete from settlement_history_tb where shop_id = ?", shop.getId());
        deleteDemoData(List.of(userA.getId(), userB.getId(), shopOwner.getId()));
        // 지난 시연에서 쌓인 알림도 지운다. 남겨 두면 이미 사라진 신청·구매를 가리키는 알림이 계속 남는다.
        jdbcTemplate.update("delete from notifications_tb where receiver_id in (?, ?, ?, ?, ?)",
            userA.getId(), userB.getId(), shopOwner.getId(), shopOwnerB.getId(), admin.getId());
        deleteDemoPayments();
        seed(userA, userB, shopOwner, shop);
        seedBranches();
        seedSubscriptionPayments();
        // 목록 화면(가게·상품·교환 게시판·문의)을 채우는 샘플 데이터. 이미 있으면 건드리지 않는다
        demoSampleDataService.seed();
        spreadAccountDates();
        seedBoards(userA, userB, shopOwner, shop);
        seedSupportInquiries(userA, userB, admin);
        seedNotifications(userA, userB, shopOwner, shop);
    }


    /**
     * 문의·리뷰 시연 데이터. 방금 만든 시연 구매·상품·이벤트·경매를 가리키게 만들어서
     * 상세 화면의 문의/리뷰 탭, 마이페이지 "내 문의", 판매자 "문의 관리"·"리뷰 관리"를 바로 볼 수 있다.
     * - 리뷰: 시연 구매자(B)의 구매확정 구매마다 1개 (구매 1건당 1개). 마지막 하나는 판매자가 비공개로 바꾼 상태
     * - 문의: 상품·이벤트·경매에 각각 남기고, 일부는 판매자가 답변해서 답변 대기/완료가 섞이게 한다
     * 초기화할 때 deleteDemoData 가 상품에 달린 문의·리뷰를 함께 지우므로 매번 새로 만든다.
     */
    private void seedBoards(User userA, User userB, User shopOwner, Shop shop) {
        Long sellerId = shopOwner.getId();
        List<Item> onSale = itemRepository.findAll().stream()
            .filter(i -> sellerId.equals(i.getSellerUserId()) && i.getStatus() == ItemStatus.ON_SALE)
            .limit(4).toList();
        List<ShopEvent> events = shopEventRepository.findAll().stream()
            .filter(e -> e.getShop() != null && shop.getId().equals(e.getShop().getId())).limit(2).toList();
        List<Auction> auctions = auctionRepository.findAll().stream()
            .filter(a -> a.getShop() != null && shop.getId().equals(a.getShop().getId()) && a.getItem() != null).limit(2).toList();

        // ----- 리뷰 (구매확정한 구매 1건당 1개) -----
        String[][] reviews = {
            {"포장이 꼼꼼해서 좋았어요", "상품 상태가 설명 그대로였고 박스도 단단하게 포장돼 왔어요."},
            {"배송이 빨랐어요", "주문 다음 날 도착했어요. 실물이 사진보다 더 예뻐요."},
            {"기대 이상이에요", "퀄리티가 좋아서 만족해요. 다음에도 이 가게에서 구매할 예정이에요."},
            {"구성품 모두 확인했어요", "구성품이 전부 들어 있었고 상태도 깨끗했어요. 추천합니다."}
        };
        List<Purchase> confirmed = purchaseRepository.findAll().stream()
            .filter(p -> p.getStatus() == PurchaseStatus.CONFIRMED && p.getUser().getId().equals(userB.getId())
                && sellerId.equals(p.getItem().getSellerUserId()))
            .limit(reviews.length).toList();
        for (int n = 0; n < confirmed.size(); n++) {
            Purchase purchase = confirmed.get(n);
            Board review = boardRepository.save(Board.builder()
                .title(reviews[n][0]).content(reviews[n][1]).authorId(userB.getId())
                .itemId(purchase.getItem().getId()).boardType(BoardType.PRODUCT_REVIEW).isSecret(false)
                .purchaseId(purchase.getId()).build());
            // 판매자가 비공개로 바꾼 리뷰도 하나 보이게 한다 (리뷰 관리의 "비공개" 표시, 작성자에게만 보이는 모습)
            if (n == confirmed.size() - 1 && confirmed.size() > 1) review.hideBySeller();
            setBoardDate(review, n + 1, 3);
        }

        // ----- 문의: 상품 -----
        String sellerReply = "안녕하세요, 데모 굿즈샵입니다. 문의 감사합니다! ";
        if (onSale.size() > 0) answer(inquiry(userA, onSale.get(0), InquiryTargetType.ITEM, onSale.get(0).getId(), "재고가 더 있나요?",
            "같은 상품을 하나 더 구매하고 싶은데 재고가 있을까요?", 2), shopOwner, sellerReply + "현재 1개 더 있어요. 편하게 주문해 주세요.");
        if (onSale.size() > 1) inquiry(userA, onSale.get(1), InquiryTargetType.ITEM, onSale.get(1).getId(), "직거래도 가능한가요?",
            "매장에 방문해서 직접 받아갈 수 있을까요?", 1);
        if (onSale.size() > 2) inquiry(userB, onSale.get(2), InquiryTargetType.ITEM, onSale.get(2).getId(), "포장은 어떻게 해서 보내주시나요?",
            "선물용이라 파손 없이 포장해서 보내주실 수 있는지 궁금해요.", 1);
        if (onSale.size() > 3) answer(inquiry(userB, onSale.get(3), InquiryTargetType.ITEM, onSale.get(3).getId(), "실물 사진을 받아볼 수 있을까요?",
            "구매 전에 실물 사진을 한 장만 받아볼 수 있을까요?", 3), shopOwner, sellerReply + "상세 이미지 아래에 실물 사진을 추가해 두었어요.");

        // ----- 문의: 이벤트 -----
        if (events.size() > 0) {
            ShopEvent event = events.get(0);
            answer(inquiry(userA, event.getItem(), InquiryTargetType.EVENT, event.getId(), "당첨 후 수령은 어떻게 하나요?",
                "이벤트에 참가하면 상품은 매장에서 바로 받아가는 건가요?", 2), shopOwner, sellerReply + "방문 날짜에 번호표를 보여주시면 현장에서 바로 드려요.");
            inquiry(userB, event.getItem(), InquiryTargetType.EVENT, event.getId(), "방문 날짜를 바꿀 수 있나요?",
                "그날 일정이 있어서 하루 뒤에 방문해도 되는지 궁금해요.", 1);
        }
        if (events.size() > 1) {
            ShopEvent event = events.get(1);
            inquiry(userB, event.getItem(), InquiryTargetType.EVENT, event.getId(), "정원이 차면 취소 대기가 되나요?",
                "마감 후에 취소하는 분이 생기면 대기 순번으로 참가할 수 있는지 궁금해요.", 1);
        }

        // ----- 문의: 경매 -----
        if (auctions.size() > 0) {
            Auction auction = auctions.get(0);
            answer(inquiry(userA, auction.getItem(), InquiryTargetType.AUCTION, auction.getId(), "입찰을 취소할 수 있나요?",
                "실수로 높게 입찰했는데 취소할 수 있는지 알고 싶어요.", 2), shopOwner, sellerReply + "입찰은 취소할 수 없어요. 입찰 전에 금액을 꼭 확인해 주세요.");
            inquiry(userB, auction.getItem(), InquiryTargetType.AUCTION, auction.getId(), "보증금은 낙찰되지 않으면 바로 환불되나요?",
                "낙찰이 안 되면 보증금이 언제 환불되는지 궁금해요.", 1);
        }
        if (auctions.size() > 1) {
            Auction auction = auctions.get(1);
            inquiry(userA, auction.getItem(), InquiryTargetType.AUCTION, auction.getId(), "배송비는 따로 있나요?",
                "낙찰가 외에 배송비가 따로 붙는지 궁금해요.", 1);
        }
    }

    // 고객센터 시연 문의 (관리자 "고객센터 문의" 화면용). 상품에 달린 글이 아니라 deleteDemoData 가 지우지 않으므로
    // 같은 제목의 시연 글을 지우고 다시 만든다. 답변 완료 / 답변 후 추가 질문(답변 대기) / 답변 대기가 섞이게 한다.
    private static final String[][] SUPPORT_INQUIRIES = {
        {"결제 영수증은 어디서 받을 수 있나요?", "상품을 구매했는데 카드 결제 영수증을 따로 받을 수 있는지 궁금해요."},
        {"교환 노쇼 페널티 기준이 궁금해요", "교환 약속 시간에 늦으면 노쇼로 처리되나요? 몇 분까지 기다려 주는지 알고 싶어요."},
        {"가게 입점 신청은 어떻게 하나요?", "오프라인 굿즈샵을 운영하고 있는데 입점하려면 어떤 서류가 필요한가요?"},
        {"알림이 오지 않아요", "교환 신청을 받았는데 알림센터에 알림이 표시되지 않았어요. 확인 부탁드려요."}
    };

    private void seedSupportInquiries(User userA, User userB, User admin) {
        for (String[] q : SUPPORT_INQUIRIES) {
            jdbcTemplate.update("delete from reply_tb where board_id in (select id from board_tb where board_type = 'CUSTOMER_SERVICE' and title = ?)", q[0]);
            jdbcTemplate.update("delete from board_tb where board_type = 'CUSTOMER_SERVICE' and title = ?", q[0]);
        }
        String hello = "안녕하세요, GoodsBridge 고객센터입니다. ";

        // 답변 완료
        Board receipt = support(userA, SUPPORT_INQUIRIES[0], 3);
        answer(receipt, admin, hello + "마이페이지 > 구매 내역에서 구매 건을 누르면 결제 정보와 함께 영수증을 확인할 수 있어요.");

        // 관리자가 답변한 뒤 작성자가 추가 질문을 남겨 다시 답변 대기
        Board noShow = support(userB, SUPPORT_INQUIRIES[1], 2);
        answer(noShow, admin, hello + "약속 시간 기준으로 가게가 출석 체크를 하지 못하면 노쇼로 처리돼요.");
        noShow.addReply(Reply.builder().board(noShow).content("그럼 가게에 미리 연락하면 시간을 미룰 수 있나요?").authorId(userB.getId()).build());
        noShow.pending();
        boardRepository.save(noShow);

        // 답변 대기
        support(userB, SUPPORT_INQUIRIES[2], 1);
        support(userA, SUPPORT_INQUIRIES[3], 0);
    }

    private Board support(User author, String[] q, int daysAgo) {
        Board board = boardRepository.save(Board.builder()
            .title(q[0]).content(q[1]).authorId(author.getId())
            .boardType(BoardType.CUSTOMER_SERVICE).isSecret(true).build());
        setBoardDate(board, daysAgo, 2);
        return board;
    }

    private Board inquiry(User author, Item item, InquiryTargetType type, Long targetId, String title, String content, int daysAgo) {
        Board board = boardRepository.save(Board.builder()
            .title(title).content(content).authorId(author.getId()).itemId(item.getId())
            .boardType(BoardType.PRODUCT_INQUIRY).isSecret(true).targetType(type).targetId(targetId).build());
        setBoardDate(board, daysAgo, 5);
        return board;
    }

    // 판매자가 답변하면 문의는 답변 완료가 된다 (BoardService.createReply 와 같은 결과)
    private void answer(Board board, User seller, String content) {
        board.addReply(Reply.builder().board(board).content(content).authorId(seller.getId()).build());
        board.complete();
        boardRepository.save(board);
    }

    // 목록의 날짜가 오늘 하루에 몰리지 않도록 며칠 전 날짜로 돌려놓는다
    private void setBoardDate(Board board, int daysAgo, int hoursAgo) {
        boardRepository.flush();
        jdbcTemplate.update("update board_tb set created_at = ? where id = ?",
            java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(daysAgo).minusHours(hoursAgo)), board.getId());
    }

    /**
     * 알림센터 시연용 알림. 방금 만든 시연 데이터(구매·교환·경매·환불)를 실제로 가리키게 만들어서
     * 알림을 눌렀을 때 알맞은 화면으로 이동하는지 바로 볼 수 있다. 초기화할 때마다 지우고 다시 만든다.
     * 가장 오래된 알림 하나는 읽음으로 두어 읽음/안 읽음이 함께 보이게 한다.
     */
    private void seedNotifications(User userA, User userB, User shopOwner, Shop shop) {
        List<Notification> made = new ArrayList<>();
        Long a = userA.getId();
        Long b = userB.getId();

        // 신청B의 구매: 배송 단계별로 하나씩 (같은 구매를 가리키는 알림은 단계가 달라도 현재 단계 탭으로 열린다)
        Long preparing = firstId("select id from purchase_tb where buyer_user_id = ? and delivery_status = 'PREPARING' and status = 'PAID' order by id desc limit 1", b);
        Long shipped = firstId("select id from purchase_tb where buyer_user_id = ? and delivery_status = 'SHIPPED' and status = 'PAID' order by id desc limit 1", b);
        Long delivered = firstId("select id from purchase_tb where buyer_user_id = ? and delivery_status = 'DELIVERED' and status = 'PAID' order by id desc limit 1", b);
        // 교환: A는 내 글에 들어온 신청, B는 내가 한 신청의 결과
        Long incoming = firstId("select r.id from exchange_request_tb r join exchange_item_tb e on e.id = r.exchange_itemaid "
            + "where e.user_id = ? and r.status = 'PENDING' order by r.id desc limit 1", a);
        Long confirmed = firstId("select r.id from exchange_request_tb r join item_tb i on i.id = r.itembid "
            + "where i.seller_user_id = ? and r.status = 'CONFIRMED' order by r.id desc limit 1", b);
        Long rejected = firstId("select r.id from exchange_request_tb r join item_tb i on i.id = r.itembid "
            + "where i.seller_user_id = ? and r.status = 'REJECTED' order by r.id desc limit 1", b);
        Long won = firstId("select id from auction_tb where current_winner_user_id = ? and status = 'ENDED_SUCCESS' order by id desc limit 1", b);
        Long refundRejected = firstId("select id from refund_tb where requester_user_id = ? and status = 'REJECTED' order by id desc limit 1", b);

        if (incoming != null) {
            made.add(notification(userA, Notification.Type.EXCHANGE_REQUEST, "[교환신청]", "(테스트) 교환 게시글에 교환 신청이 들어왔습니다",
                Notification.ReferenceType.EXCHANGE, incoming));
        }
        if (confirmed != null) {
            made.add(notification(userB, Notification.Type.EXCHANGE_APPROVED, "[교환승인]", "교환 신청이 수락되었습니다. 가게에서 만나 교환해 주세요.",
                Notification.ReferenceType.EXCHANGE, confirmed));
        }
        if (rejected != null) {
            made.add(notification(userB, Notification.Type.EXCHANGE_REJECTED, "[교환거절]", "교환 신청이 거절되었습니다",
                Notification.ReferenceType.EXCHANGE, rejected));
        }
        if (won != null) {
            made.add(notification(userB, Notification.Type.AUCTION_WIN, "[상품 낙찰]", "(테스트) 경매 상품에 낙찰되셨습니다! 기한 내 잔금을 결제해 주세요.",
                Notification.ReferenceType.AUCTION, won));
        }
        if (preparing != null) {
            made.add(notification(userB, Notification.Type.PAYMENT_COMPLETED, "[배송준비중]", "(테스트) 상품이 구매가 완료되었습니다",
                Notification.ReferenceType.DELIVERY, preparing));
        }
        if (shipped != null) {
            made.add(notification(userB, Notification.Type.DELIVERY_STARTED, "[배송시작]", "(테스트) 상품이 배송 중입니다",
                Notification.ReferenceType.DELIVERY, shipped));
        }
        if (delivered != null) {
            made.add(notification(userB, Notification.Type.DELIVERY_COMPLETED, "[배송완료]", "(테스트) 상품이 배송 완료되었습니다. 구매를 확정해 주세요.",
                Notification.ReferenceType.DELIVERY, delivered));
        }
        if (refundRejected != null) {
            made.add(notification(userB, Notification.Type.REFUND_REJECTED, "[환불거절]", "환불 요청이 거절되었습니다",
                Notification.ReferenceType.REFUND, refundRejected));
        }

        // 가게 사장님: 교환 예약 알림 (실제로 가게가 받는 알림은 이것뿐이다. 경매 입찰 알림은 아직 발송되지 않는다)
        List<Long> booked = jdbcTemplate.queryForList(
            "select id from exchange_request_tb where shop_id = ? and status = 'CONFIRMED' order by id desc limit 2", Long.class, shop.getId());
        for (Long requestId : booked) {
            made.add(notification(shopOwner, Notification.Type.EXCHANGE_BOOKED, "[교환예약]", "(테스트) 교환 예약 일정이 생겼습니다. 방문하면 출석을 확인해 주세요.",
                Notification.ReferenceType.SHOP_EXCHANGE, requestId));
        }

        // 만든 순서대로 몇 분씩 간격을 두고 과거로 흩어 놓고, 가장 오래된 알림은 읽음으로 둔다
        for (int i = 0; i < made.size(); i++) {
            jdbcTemplate.update("update notifications_tb set created_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(5L + i * 20L)), made.get(i).getId());
        }
        if (made.size() > 1) {
            jdbcTemplate.update("update notifications_tb set read_status = 'READ' where id = ?", made.get(made.size() - 1).getId());
        }
    }

    private Notification notification(User receiver, Notification.Type type, String title, String content,
                                      Notification.ReferenceType referenceType, Long referenceId) {
        return notificationRepository.save(new Notification(receiver, type, title, content, referenceType, referenceId));
    }

    // 첫 번째 행의 id (없으면 null)
    private Long firstId(String sql, Long userId) {
        List<Long> ids = jdbcTemplate.queryForList(sql, Long.class, userId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    // 시연 계정과 시연 가게의 가입일·입점일을 과거로 흩어 놓는다 (샘플 데이터와 같은 이유: 대시보드 차트가 DB마다 같은 모양이 되게)
    private void spreadAccountDates() {
        Object[][] accounts = {
            { ADMIN_EMAIL, 165 }, { SHOP_EMAIL, 150 }, { USER_A_EMAIL, 130 }, { USER_B_EMAIL, 110 }, { SHOP_B_EMAIL, 70 },
        };
        for (Object[] a : accounts) {
            jdbcTemplate.update("update user_tb set created_at = ? where email = ?", daysAgoAt10((int) a[1]), a[0]);
        }
        for (int n = 1; n <= 6; n++) {
            jdbcTemplate.update("update user_tb set created_at = ? where email = ?", daysAgoAt10(100 - n * 10),
                "demo-branch-" + n + "@goodsbridge.demo");
        }
        // 데모 굿즈샵과 B는 새 DB에서는 승인 시각이 '지금'으로 찍히므로 고정된 과거로 맞춘다
        jdbcTemplate.update("update shop_tb set approved_at = ?, created_at = ? where owner_user_id = (select id from user_tb where email = ?) and status = 'APPROVED' and deleted = 0",
            daysAgoAt10(95), daysAgoAt10(96), SHOP_EMAIL);
        jdbcTemplate.update("update shop_tb set approved_at = ?, created_at = ? where owner_user_id = (select id from user_tb where email = ?) and status = 'APPROVED' and deleted = 0",
            daysAgoAt10(45), daysAgoAt10(46), SHOP_B_EMAIL);
    }

    private Timestamp daysAgoAt10(int days) {
        return Timestamp.valueOf(LocalDate.now().minusDays(days).atTime(10, 0));
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
            // 승인된 지점은 입점 승인일을 신청 며칠 전(승인 하루 전 신청)으로 과거에 흩어 놓아서, 입점 관리 달력으로 월별 조회를 시연할 수 있게 한다
            if ("APPROVED".equals(b[3])) {
                LocalDateTime approvedAt = LocalDateTime.now().minusDays(Integer.parseInt(b[5])).minusHours(n + 1);
                jdbcTemplate.update("update shop_tb set created_at = ?, approved_at = ? where id = ?",
                    Timestamp.valueOf(approvedAt.minusDays(1)), Timestamp.valueOf(approvedAt), branch.getId());
            }

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

    // 구독 요금 (기획서 3.1: 1일 1,000원)
    private static final int SUBSCRIPTION_FEE_PER_DAY = 1_000;

    // 관리자 대시보드의 구독 매출·결제 건수를 시연할 수 있게 구독 결제 기록(SHOP_SUBSCRIPTION)을 만든다.
    // 시연 구독마다 시작일에 결제한 것으로 기록하고, 최근 몇 달에 걸친 연장 결제 이력도 함께 넣는다.
    // 결제 번호가 demo- 로 시작해서 초기화할 때마다 deleteDemoPayments()가 지우고 다시 만든다.
    private void seedSubscriptionPayments() {
        // 1) 시연 구독(데모 가게, 지점들)의 최초 결제: 구독 기간 길이만큼, 시작일에 결제
        jdbcTemplate.query(
            "select sub.payment_id, sub.start_date, sub.end_date, s.owner_user_id "
                + "from shop_subscription_tb sub join shop_tb s on s.id = sub.shop_id "
                + "where sub.payment_id like 'demo-subscription-%'",
            rs -> {
                long days = java.time.temporal.ChronoUnit.DAYS.between(
                    rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate());
                insertSubscriptionPayment(rs.getString("payment_id"), rs.getLong("owner_user_id"),
                    (int) days * SUBSCRIPTION_FEE_PER_DAY, rs.getDate("start_date").toLocalDate());
            });

        // 2) 최근 몇 달의 연장 결제 이력 { 시연 계정, 몇 달 전, 일, 구독 일수 }
        User shopOwner = findOrCreateUser(SHOP_EMAIL, "데모가게");
        Object[][] history = {
            { "demo-branch-4@goodsbridge.demo", 5, 8, 30 },
            { "demo-branch-4@goodsbridge.demo", 4, 8, 30 },
            { "demo-branch-4@goodsbridge.demo", 3, 8, 60 },
            { "demo-branch-5@goodsbridge.demo", 5, 15, 90 },
            { "demo-branch-5@goodsbridge.demo", 2, 15, 90 },
            { "demo-branch-6@goodsbridge.demo", 5, 20, 180 },
            { "demo-branch-6@goodsbridge.demo", 0, 2, 60 },
            { SHOP_EMAIL, 4, 22, 60 },
            { SHOP_EMAIL, 1, 5, 30 },
        };
        LocalDate today = LocalDate.now();
        for (Object[] h : history) {
            Long ownerId = h[0].equals(SHOP_EMAIL) ? shopOwner.getId()
                : userRepository.findByEmail((String) h[0]).map(User::getId).orElse(null);
            if (ownerId == null) continue;
            java.time.YearMonth month = java.time.YearMonth.from(today).minusMonths((int) h[1]);
            LocalDate paidOn = month.atDay(Math.min((int) h[2], month.lengthOfMonth()));
            if (paidOn.isAfter(today)) paidOn = today;   // 이번 달 결제가 미래 날짜가 되지 않게
            insertSubscriptionPayment("demo-subscription-history-" + UUID.randomUUID(), ownerId,
                (int) h[3] * SUBSCRIPTION_FEE_PER_DAY, paidOn);
        }
    }

    // JPA의 @CreationTimestamp는 결제일을 지금으로 덮어쓰므로, 과거 날짜로 넣으려고 JDBC로 직접 넣는다.
    private void insertSubscriptionPayment(String paymentId, Long userId, int amount, LocalDate paidOn) {
        jdbcTemplate.update(
            "insert into payment_tb (payment_id, pg_tx_id, user_id, amount, refunded_amount, purpose, status, created_at) "
                + "values (?, ?, ?, ?, 0, 'SHOP_SUBSCRIPTION', 'PAID', ?)",
            paymentId, "demo-tx-" + paymentId, userId, amount, Timestamp.valueOf(paidOn.atTime(10, 0)));
    }

    // 시연 계정이 만든 상품/교환글과 거기에 딸린 데이터를 외래키 순서대로 지운다.
    // 시연 결제(demo- 로 시작)는 초기화할 때마다 새로 만들어지는데 지워지지 않아 결제 기록이 계속 쌓였다.
    // 위에서 시연 구매·참가·신청을 지웠으니 남은 시연 결제와, 그 결제를 가리키는 환불·정산 기록을 함께 지운다. (실제 PortOne 결제는 건드리지 않는다)
    private void deleteDemoPayments() {
        String demoPayments = "(select id from payment_tb where payment_id like 'demo-%')";
        jdbcTemplate.update("delete from refund_tb where payment_id in " + demoPayments);
        jdbcTemplate.update("delete from settlement_history_tb where payment_id in " + demoPayments);
        jdbcTemplate.update("delete from payment_tb where payment_id like 'demo-%'");
    }

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
        // 상품 문의·리뷰 게시글(댓글 포함)도 지운다. 상품·구매·이벤트·경매가 새로 만들어지면 옛 글이 가리킬 곳이 없어진다.
        // (MySQL 은 같은 테이블을 서브쿼리로 참조하면서 지울 수 없어서, 게시글은 조건을 직접 쓴다)
        jdbcTemplate.update("delete from reply_tb where board_id in (select id from board_tb where item_id in " + items
            + " and board_type in ('PRODUCT_INQUIRY', 'PRODUCT_REVIEW'))");
        jdbcTemplate.update("delete from board_tb where item_id in " + items
            + " and board_type in ('PRODUCT_INQUIRY', 'PRODUCT_REVIEW')");
        jdbcTemplate.update("delete from purchase_tb where item_id in " + items);
        jdbcTemplate.update("delete from bid_tb where auction_id in (select id from auction_tb where item_id in " + items + ")");
        jdbcTemplate.update("delete from auction_tb where item_id in " + items);
        jdbcTemplate.update("delete from event_participation_tb where shop_event_id in (select id from shop_event_tb where item_id in " + items + ")");
        jdbcTemplate.update("delete from shop_event_tb where item_id in " + items);
        jdbcTemplate.update("delete from item_tb where seller_user_id in " + u);
    }

    private void seed(User userA, User userB, User shopOwner, Shop shop) {
        // 시연 카테고리는 이름으로 찾고, 없으면 만든다 (DB 에 다른 카테고리가 있어도 시연 데이터는 같게 나온다)
        List<Category> existing = categoryRepository.findAll();
        List<Category> categories = DEMO_CATEGORIES.stream()
            .map(name -> existing.stream().filter(c -> name.equals(c.getName())).findFirst()
                .orElseGet(() -> categoryRepository.save(Category.builder().name(name).build())))
            .toList();
        List<String> images = DEMO_PHOTOS;
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
        seedAuctions(shopOwner, shop, categories, images, userA, userB);

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

    // ===== 경매 시연 데이터 =====
    // 진행중(내가 최고가 / 다른 사람이 최고가 / 입찰 없음), 종료(낙찰 · 잔금 대기 / 낙찰 · 결제 완료 / 유찰), 취소
    // 마감이 지난 경매는 상태만 바꿔 두고(스케줄러가 시연 데이터를 건드리지 않게) 패찰자 보증금은 환불 처리한다.
    private void seedAuctions(User shopOwner, Shop shop, List<Category> categories, List<String> images, User userA, User userB) {
        if (categories.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        Category c0 = categories.get(0), c1 = categories.get(1 % categories.size()), c2 = categories.get(2 % categories.size());

        // 1) 진행중 · 데모교환A 가 최고가 (D-2)
        Auction live1 = saveAuction(shopOwner, shop, c0, "(테스트) D-Series 한정판 피규어 풀박스", "한정판 넨도로이드 스타일 피규어 풀박스 세트입니다. 박스 상태 최상, 미개봉.",
            50000, 7500, now.plusDays(2).plusHours(3), "/img/auction-figure-box.jpg");
        Payment depB1 = bid(live1, userB, 52000, null);
        Payment depA1 = bid(live1, userA, 58000, null);
        bid(live1, userB, 61000, depB1);
        bid(live1, userA, 66000, depA1);

        // 2) 진행중 · 데모신청B 가 최고가 (오늘 마감)
        Auction live2 = saveAuction(shopOwner, shop, c1, "(테스트) D-Series 스탠딩 인형 한정판", "스탠드 포함 한정판 스탠딩 인형입니다. 의상과 소품 모두 포함.",
            30000, 4500, now.plusHours(4), "/img/auction-plush.jpg");
        Payment depA2 = bid(live2, userA, 33000, null);
        Payment depB2 = bid(live2, userB, 36000, null);
        bid(live2, userA, 38000, depA2);
        bid(live2, userB, 41000, depB2);

        // 3) 진행중 · 입찰 없음 (가게가 취소할 수 있음)
        saveAuction(shopOwner, shop, c2, "(테스트) 사인 포토카드 세트 (입찰 전)", "시연용 경매입니다. 아직 입찰이 없어서 가게가 취소할 수 있어요.",
            20000, 3000, now.plusDays(5), "/img/auction-figure-box-2.jpg");

        // 4) 종료 · 낙찰 → 데모신청B 가 잔금을 결제해야 함 (결제 기한은 하루 뒤)
        Auction won = saveAuction(shopOwner, shop, c0, "(테스트) 한정판 아크릴 스탠드 세트", "낙찰 후 잔금 결제를 기다리는 시연용 경매입니다.",
            25000, 3750, now.minusHours(1), images.get(1 % images.size()));
        bid(won, userA, 28000, null);
        bid(won, userB, 41000, null);
        finishAuction(won, now.plusDays(1));

        // 5) 종료 · 낙찰 → 데모신청B 가 잔금까지 결제 완료 (구매 내역·정산 기록 포함, B 로 배송·구매확정까지 시연)
        Auction paid = saveAuction(shopOwner, shop, c1, "(테스트) 일러스트 포스터 세트", "잔금 결제까지 끝난 시연용 경매입니다.",
            28000, 4200, now.minusDays(2), images.get(2 % images.size()));
        bid(paid, userA, 30000, null);   // A 는 패찰 (보증금 환불)
        bid(paid, userB, 38000, null);
        finishAuction(paid, now.minusDays(1));
        payAuctionBalance(paid, userB, now.minusDays(2).plusHours(5));

        // 6) 종료 · 유찰 (입찰 없음)
        Auction failed = saveAuction(shopOwner, shop, c2, "(테스트) 응원봉 키링 (유찰)", "입찰이 없어서 유찰된 시연용 경매입니다.",
            15000, 2250, now.minusDays(3), images.get(3 % images.size()));
        failed.end();
        auctionRepository.save(failed);

        // 7) 취소
        Auction cancelled = saveAuction(shopOwner, shop, c0, "(테스트) 취소된 경매 상품", "가게가 취소한 시연용 경매입니다.",
            22000, 3300, now.plusDays(1), images.get(4 % images.size()));
        cancelled.cancel();
        auctionRepository.save(cancelled);

        // 9) 종료 · 데모신청B 는 패찰 (데모교환A 가 낙찰해 잔금까지 결제, B 의 보증금은 환불)
        Auction lost = saveAuction(shopOwner, shop, c0, "(테스트) 아크릴 홀더 세트 (패찰)", "데모신청B 가 패찰한 시연용 경매입니다.",
            20000, 3000, now.minusDays(5), images.get(6 % images.size()));
        bid(lost, userB, 22000, null);
        bid(lost, userA, 27000, null);
        finishAuction(lost, now.minusDays(4));
        payAuctionBalance(lost, userA, now.minusDays(4).plusHours(3));

        // 10~14) 종료 · 데모신청B 가 낙찰해 잔금까지 결제한 경매의 배송 · 구매 확정 · 환불 단계별 시연
        // (가게 '판매 상품 관리'의 판매완료와 B 의 '구매 내역'에 경매 낙찰로 보인다)
        String[][] orders = {
            {"(테스트) 한정판 키링 세트 (배송중)", "SHIPPED", "서울시 마포구 데모로 12, 101호"},
            {"(테스트) 캐릭터 엽서 박스 (배송 완료)", "DELIVERED", "서울시 마포구 데모로 12, 101호"},
            {"(테스트) 일러스트 아트북 (구매 확정)", "CONFIRMED", "서울시 마포구 데모로 12, 101호"},
            {"(테스트) 한정판 포카 바인더 (환불 요청중)", "REFUND_REQUESTED", "서울시 마포구 데모로 12, 101호"},
            {"(테스트) 아크릴 무드등 (환불 완료)", "REFUNDED", "서울시 마포구 데모로 12, 101호"},
            {"(테스트) 캐릭터 머그컵 (환불 요청중)", "REFUND_REQUESTED", "서울시 마포구 데모로 12, 101호"},
            {"(테스트) 한정판 스티커 팩 (환불 거절)", "REFUND_REJECTED", "서울시 마포구 데모로 12, 101호"},
        };
        for (int i = 0; i < orders.length; i++) {
            Auction order = saveAuction(shopOwner, shop, categories.get((i + 3) % categories.size()), orders[i][0], "낙찰 후 잔금까지 결제한 시연용 경매입니다. (" + orders[i][1] + ")",
                24000 + i * 2000, 3600 + i * 300, now.minusDays(6 + i), images.get((7 + i) % images.size()));
            bid(order, userA, 25000 + i * 2000, null);
            bid(order, userB, 30000 + i * 2500, null);
            finishAuction(order, now.minusDays(5 + i));
            seedAuctionOrder(order, userB, now.minusDays(5 + i).plusHours(2), orders[i][1], "CJ" + (700100200300L + i), orders[i][2]);
        }

        // 경매 전용으로 등록만 해 두고 아직 경매에 올리지 않은 상품 2개 ('경매 열기'에서 고를 수 있다)
        String[][] spare = {{"(테스트) 한정판 아크릴 인형 (경매 대기)", "28000"}, {"(테스트) 캐릭터 일러스트 액자 (경매 대기)", "35000"}};
        for (int i = 0; i < spare.length; i++) {
            itemRepository.save(Item.builder()
                .sellerUserId(shopOwner.getId()).shopId(shop.getId()).category(categories.get((i + 5) % categories.size()))
                .title(spare[i][0]).description("경매 전용으로 등록해 둔 상품입니다. 경매 열기에서 골라 경매로 올릴 수 있어요.")
                .price(Integer.parseInt(spare[i][1])).saleType(SaleType.AUCTION).status(ItemStatus.ON_SALE)
                .imageUrl(images.get((12 + i) % images.size()))
                .build());
        }

        // 8) 종료 · 낙찰자가 기한 안에 잔금을 내지 않음 → 낙찰 무효, 낙찰자의 보증금만 가게 정산
        Auction forfeited = saveAuction(shopOwner, shop, c1, "(테스트) 캐릭터 아크릴 키링 (잔금 미결제)", "낙찰자가 기한 안에 잔금을 내지 않아 보증금만 정산된 시연용 경매입니다.",
            18000, 2700, now.minusDays(4), images.get(5 % images.size()));
        bid(forfeited, userA, 19000, null);
        Payment forfeitedDeposit = bid(forfeited, userB, 24000, null);
        finishAuction(forfeited, now.minusDays(2));
        forfeited.forfeitUnpaid();
        auctionRepository.save(forfeited);
        recordFee(shop, forfeitedDeposit, forfeited.getDepositAmount(), SettlementHistoryType.AUCTION_DEPOSIT_FORFEIT);
        settlementHistoryRepository.flush();
        jdbcTemplate.update("update settlement_history_tb set created_at = ? where payment_id = ? and type = 'AUCTION_DEPOSIT_FORFEIT'",
            Timestamp.valueOf(now.minusDays(2)), forfeitedDeposit.getId());
    }

    private Auction saveAuction(User shopOwner, Shop shop, Category category, String title, String description,
                                int startPrice, int deposit, LocalDateTime deadline, String imageUrl) {
        Item item = itemRepository.save(Item.builder()
            .sellerUserId(shopOwner.getId())
            .shopId(shop.getId())
            .category(category)
            .title(title)
            .description(description)
            .price(startPrice)
            .saleType(SaleType.AUCTION)
            .status(ItemStatus.ON_SALE)
            .imageUrl(imageUrl)
            .build());
        return auctionRepository.save(Auction.builder()
            .shop(shop).item(item).startPrice(startPrice).deadline(deadline).depositAmount(deposit)
            .build());
    }

    // 입찰 한 건. 그 경매의 첫 입찰이면 보증금 결제를 만들고, 이미 낸 사람은 같은 보증금 결제를 다시 쓴다.
    private Payment bid(Auction auction, User user, int amount, Payment existingDeposit) {
        Payment deposit = existingDeposit != null ? existingDeposit
            : paymentRepository.save(demoPayment(user, auction.getDepositAmount(), PaymentPurpose.AUCTION_DEPOSIT));
        bidRepository.save(Bid.builder().auction(auction).user(user).bidAmount(amount).depositPaymentId(deposit.getPaymentId()).build());
        auction.updateCurrentBid(amount, user);
        auction.setWinningPaymentId(deposit.getPaymentId());
        auctionRepository.save(auction);
        return deposit;
    }

    // 마감 처리: 낙찰 상태로 바꾸고 결제 기한을 정한 뒤, 패찰자 보증금은 환불된 것으로 둔다
    private void finishAuction(Auction auction, LocalDateTime balanceDueAt) {
        auction.end();
        auction.markLosersRefunded();
        auctionRepository.save(auction);
        auctionRepository.flush();
        jdbcTemplate.update("update auction_tb set balance_due_at = ? where id = ?", Timestamp.valueOf(balanceDueAt), auction.getId());
        // 낙찰자가 아닌 입찰자의 보증금은 돌려준 상태로 만든다
        for (String paymentId : bidRepository.findLoserDepositPaymentIds(auction.getId(), auction.getCurrentWinner().getId())) {
            paymentRepository.findByPaymentId(paymentId).ifPresent(p -> p.refund(auction.getDepositAmount()));
        }
    }

    // 낙찰자의 잔금 결제 완료: 잔금 결제, 구매 내역, 가게 정산(낙찰가 전액)까지 기록
    private void payAuctionBalance(Auction auction, User winner, LocalDateTime paidAt) {
        Payment balance = paymentRepository.save(demoPayment(winner, auction.getBalanceAmount(), PaymentPurpose.AUCTION_BALANCE));
        auction.completeBalance(balance.getPaymentId());
        auction.getItem().updateStatus(ItemStatus.SOLD);
        auctionRepository.save(auction);
        purchaseRepository.save(Purchase.builder()
            .item(auction.getItem()).user(winner).paymentId(balance.getPaymentId())
            .deliveryAddress("서울시 마포구 데모로 12, 101호").source(PurchaseSource.AUCTION).amount(auction.getCurrentPrice())
            .build());
        recordFee(auction.getShop(), balance, auction.getAuctionSettlementAmount(), SettlementHistoryType.AUCTION_SALE);
        settlementHistoryRepository.flush();
        jdbcTemplate.update("update settlement_history_tb set created_at = ? where payment_id = ? and type = 'AUCTION_SALE'",
            Timestamp.valueOf(paidAt), balance.getId());
    }

    // 낙찰자가 잔금까지 결제한 뒤의 단계: SHIPPED / DELIVERED / CONFIRMED / REFUND_REQUESTED / REFUNDED
    private void seedAuctionOrder(Auction auction, User winner, LocalDateTime paidAt, String state, String tracking, String address) {
        payAuctionBalance(auction, winner, paidAt);
        Purchase purchase = purchaseRepository.findByPaymentId(auction.getBalancePaymentId()).orElseThrow();
        purchase.ship(tracking);
        if (!state.equals("SHIPPED")) purchase.markDelivered();
        if (state.equals("CONFIRMED")) purchase.confirm();
        purchaseRepository.save(purchase);
        Payment balance = paymentRepository.findByPaymentId(auction.getBalancePaymentId()).orElseThrow();
        if (state.equals("REFUND_REQUESTED")) {
            // 경매 낙찰 상품은 낙찰가 전액(보증금 포함)만 환불할 수 있다
            demoRefund(balance, winner, auction.getCurrentPrice(), "배송된 상품의 포장이 심하게 파손되어 환불 요청합니다", RequestedBy.USER, Status.REQUESTED);
        }
        if (state.equals("REFUND_REJECTED")) {
            // 구매자가 환불을 요청했지만 가게가 상품 상태를 확인하고 거절한 경우 (가게 화면에서 '승인으로 변경'으로 재검토 가능)
            demoRefund(balance, winner, auction.getCurrentPrice(), "상품이 마음에 들지 않아 환불 요청합니다", RequestedBy.USER, Status.REQUESTED)
                .reject("상품 상태에 이상이 없고 단순 변심은 낙찰 상품 환불 대상이 아니라 거절합니다");
        }
        if (state.equals("REFUNDED")) {
            Refund refund = demoRefund(balance, winner, auction.getCurrentPrice(), "배송 중 분실되어 상품을 받지 못했어요", RequestedBy.USER, Status.REQUESTED);
            refund.approve();
            balance.refund(balance.getRefundableAmount());
            paymentRepository.findByPaymentId(auction.getWinningPaymentId()).ifPresent(d -> d.refund(d.getRefundableAmount()));
            settlementHistoryRepository.flush();
            // 전액 환불이라 가게 정산(낙찰가 전액)도 취소된 상태
            settlementHistoryRepository.deleteByPaymentAndType(balance, SettlementHistoryType.AUCTION_SALE);
        }
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
