package com.goodsbridge.demo;

import com.goodsbridge.auction.Auction;
import com.goodsbridge.auction.AuctionRepository;
import com.goodsbridge.auction.AuctionScheduler;
import com.goodsbridge.auction.AuctionService;
import com.goodsbridge.auction.BalancePayRequest;
import com.goodsbridge.auction.BidRequest;
import com.goodsbridge.purchase.DeliveryStatus;
import com.goodsbridge.purchase.PurchaseRequest;
import com.goodsbridge.purchase.PurchaseService;
import com.goodsbridge.auction.BidService;
import com.goodsbridge.event.EventService;
import com.goodsbridge.event.ShopEvent;
import com.goodsbridge.event.ShopEventRepository;
import com.goodsbridge.event.dto.EventParticipationRequest;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.SaleType;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 시연용 "시간 빨리감기": 마감 시간이 지나야 도는 스케줄러를 기다리지 않고 바로 보여준다.
// 마감 시간을 지난 것으로 당긴 뒤 실제 스케줄러 메서드를 한 번 부르고, 단계마다 상태·환불·정산·알림을 기록한다.
// 임시 데이터는 끝나면 모두 지운다.
@Service
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoTimelineService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("M/d HH:mm");
    private static final int START_PRICE = 10_000;
    private static final int DEPOSIT = 1_000;
    private static final int EVENT_FEE = 1_000;
    private static final String DELIVERY_ADDRESS = "서울시 성동구 시연로 12, 101호";
    private static final String TRACKING_NUMBER = "DEMO-1120-0001";

    private final BidService bidService;
    private final AuctionService auctionService;
    private final PurchaseService purchaseService;
    private final AuctionRepository auctionRepository;
    private final AuctionScheduler auctionScheduler;
    private final EventService eventService;
    private final ShopEventRepository shopEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final DemoTempData tempData;

    public record Step(String title, String action, Map<String, String> facts, List<String> notifications) {}

    public record Scenario(String name, List<Step> steps) {}

    // 화면에서 고른 시나리오 하나만 돌린다: auction(경매 마감 → 잔금 미납) / event(선착순 이벤트 마감 정산)
    public Scenario run(String scenario) {
        return switch (scenario) {
            case "auctionPaid" -> auction(true);
            case "auction" -> auction(false);
            case "event" -> event();
            default -> throw new com.goodsbridge._core.exception.BadRequestException("알 수 없는 시나리오입니다.");
        };
    }

    // 경매: 입찰 3건 → 마감 시간 지남(C 낙찰, A·B 보증금 환불) → 이어서
    //  paid=true : C가 배송지를 적고 잔금 결제 → 가게 발송(송장) → 배송 완료 → C 구매 확정(가게 정산)
    //  paid=false: 잔금 기한 지남(낙찰 취소, C 보증금은 가게 몫)
    private Scenario auction(boolean paid) {
        DemoTempData.Run run = tempData.start();
        try {
            record Setup(Long auctionId, Long shopId, Map<Long, String> names) {}
            Setup s = transactionTemplate.execute(status -> {
                User owner = tempData.user(run, "owner");
                Shop shop = tempData.shop(run, owner);
                Item item = tempData.item(run, owner, SaleType.AUCTION, START_PRICE, "빨리감기 경매 상품");
                Auction auction = auctionRepository.save(Auction.builder().shop(shop).item(item)
                    .startPrice(START_PRICE).depositAmount(DEPOSIT).deadline(LocalDateTime.now().plusDays(1)).build());
                Map<Long, String> names = new LinkedHashMap<>();
                names.put(owner.getId(), "가게");
                for (String n : List.of("A", "B", "C")) {
                    names.put(tempData.user(run, n).getId(), n);
                }
                return new Setup(auction.getId(), shop.getId(), names);
            });
            Long[] bidders = s.names().keySet().stream().skip(1).toArray(Long[]::new);
            int[] amounts = {START_PRICE, 12_000, 15_000};
            for (int i = 0; i < bidders.length; i++) {
                bidService.placeBid(s.auctionId(), bidders[i], new BidRequest(amounts[i], run.paymentId("bid-" + i)));
            }
            Notifications notes = new Notifications(s.names());
            notes.since();   // 입찰 단계 알림(상위 입찰 등)은 첫 단계에 보여준다
            List<Step> steps = new ArrayList<>();
            steps.add(new Step("입찰 3건", "A 10,000원 → B 12,000원 → C 15,000원 순서로 입찰 (각자 보증금 1,000원 결제)",
                auctionFacts(s.auctionId(), s.shopId(), run, paid), notes.all()));

            jdbcTemplate.update("update auction_tb set deadline = ? where id = ?", LocalDateTime.now().minusMinutes(1), s.auctionId());
            auctionScheduler.autoCloseAuctions();
            steps.add(new Step("경매 마감 시간 지남", "마감 시간을 1분 전으로 당기고 경매 스케줄러 실행",
                auctionFacts(s.auctionId(), s.shopId(), run, paid), notes.since()));

            if (!paid) {
                jdbcTemplate.update("update auction_tb set balance_due_at = ? where id = ?", LocalDateTime.now().minusMinutes(1), s.auctionId());
                auctionScheduler.autoCloseAuctions();
                steps.add(new Step("잔금 결제 기한 지남", "낙찰자 C가 잔금을 내지 않은 채 기한을 1분 전으로 당기고 경매 스케줄러 실행",
                    auctionFacts(s.auctionId(), s.shopId(), run, false), notes.since()));
                return new Scenario("경매 마감 → 잔금 미납", steps);
            }

            Long ownerId = s.names().keySet().iterator().next();
            Long winnerId = bidders[2];
            auctionService.payBalance(winnerId, s.auctionId(), new BalancePayRequest(run.paymentId("balance"), DELIVERY_ADDRESS));
            steps.add(new Step("C가 배송지를 적고 잔금 결제", "낙찰가 15,000원 - 보증금 1,000원 = 잔금 14,000원 결제, 배송지 입력 → 구매 내역 생성",
                auctionFacts(s.auctionId(), s.shopId(), run, true), notes.since()));

            Long purchaseId = jdbcTemplate.queryForObject("select id from purchase_tb where payment_id = ?", Long.class, run.paymentId("balance"));
            purchaseService.shipping(purchaseId, ownerId, shipping(TRACKING_NUMBER, null));
            steps.add(new Step("가게가 발송", "가게 사장님이 운송장 번호를 입력 → 배송중",
                auctionFacts(s.auctionId(), s.shopId(), run, true), notes.since()));

            purchaseService.shipping(purchaseId, ownerId, shipping(null, DeliveryStatus.DELIVERED));
            steps.add(new Step("배송 완료", "가게 사장님이 배송 완료 처리 → C에게 구매 확정 요청",
                auctionFacts(s.auctionId(), s.shopId(), run, true), notes.since()));

            purchaseService.confirm(purchaseId, winnerId);
            steps.add(new Step("C가 구매 확정", "C가 상품을 받고 구매 확정 → 낙찰가 전체가 가게 정산에 기록",
                auctionFacts(s.auctionId(), s.shopId(), run, true), notes.since()));
            return new Scenario("경매 낙찰 → 잔금 결제 → 배송 → 구매 확정", steps);
        } finally {
            tempData.cleanup(run);
        }
    }

    private static PurchaseRequest.ShippingDto shipping(String trackingNumber, DeliveryStatus status) {
        PurchaseRequest.ShippingDto dto = new PurchaseRequest.ShippingDto();
        dto.setTrackingNumber(trackingNumber);
        dto.setDeliveryStatus(status);
        return dto;
    }

    // withPurchase: 잔금 결제 이후 구매·배송 정보까지 보여준다 (낙찰 성공 시나리오)
    private Map<String, String> auctionFacts(Long auctionId, Long shopId, DemoTempData.Run run, boolean withPurchase) {
        Map<String, Object> a = jdbcTemplate.queryForMap(
            "select a.status, a.current_price, a.balance_due_at, a.balance_payment_id, u.email from auction_tb a "
                + "left join user_tb u on u.id = a.current_winner_user_id where a.id = ?", auctionId);
        Map<String, String> facts = new LinkedHashMap<>();
        String status = String.valueOf(a.get("status"));
        facts.put("경매 상태", "ENDED_SUCCESS".equals(status) && a.get("balance_payment_id") != null ? "낙찰 (잔금 결제 완료)" : auctionStatus(status));
        facts.put("현재 최고가", won(((Number) a.get("current_price")).intValue()));
        String email = (String) a.get("email");
        facts.put("최고 입찰자", email == null ? "-" : email.replaceAll(".*-([A-Z])@.*", "$1"));
        Object due = a.get("balance_due_at");
        facts.put("잔금 결제 기한", due == null ? "-" : toTime(due));
        if (withPurchase) {
            List<Map<String, Object>> p = jdbcTemplate.queryForList(
                "select delivery_address, tracking_number, delivery_status, status from purchase_tb where payment_id = ?", run.paymentId("balance"));
            Map<String, Object> purchase = p.isEmpty() ? Map.of() : p.get(0);
            facts.put("배송지", text(purchase.get("delivery_address")));
            facts.put("배송 상태", deliveryStatus(purchase.get("delivery_status")));
            facts.put("송장 번호", text(purchase.get("tracking_number")));
            facts.put("구매 상태", purchase.isEmpty() ? "-" : "CONFIRMED".equals(String.valueOf(purchase.get("status"))) ? "구매 확정" : "결제 완료");
        }
        String[] names = {"A", "B", "C"};
        for (int i = 0; i < names.length; i++) {
            facts.put(names[i] + " 보증금 환불", won(refunded(run.paymentId("bid-" + i))));
        }
        if (withPurchase) {
            List<Integer> paidAmount = jdbcTemplate.queryForList("select amount from payment_tb where payment_id = ?", Integer.class, run.paymentId("balance"));
            facts.put("C 잔금 납부", won(paidAmount.isEmpty() ? 0 : paidAmount.get(0)));
        }
        facts.put("가게 정산", won(settled(shopId)));
        return facts;
    }

    private static String text(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private static String deliveryStatus(Object value) {
        if (value == null) return "-";
        return switch (String.valueOf(value)) {
            case "PREPARING" -> "배송 준비";
            case "SHIPPED" -> "배송중";
            case "DELIVERED" -> "배송 완료";
            default -> String.valueOf(value);
        };
    }

    // 선착순 이벤트: 참가 3명 → 마감 시간 지남(마감·참가비를 가게 정산에 기록·알림)
    private Scenario event() {
        DemoTempData.Run run = tempData.start();
        try {
            record Setup(Long eventId, Long shopId, Map<Long, String> names) {}
            Setup s = transactionTemplate.execute(status -> {
                User owner = tempData.user(run, "owner");
                Shop shop = tempData.shop(run, owner);
                Item item = tempData.item(run, owner, SaleType.EVENT, 10_000, "빨리감기 이벤트 상품");
                ShopEvent event = shopEventRepository.save(ShopEvent.builder().shop(shop).item(item)
                    .capacity(5).feeAmount(EVENT_FEE).deadline(LocalDateTime.now().plusDays(1)).build());
                Map<Long, String> names = new LinkedHashMap<>();
                names.put(owner.getId(), "가게");
                for (String n : List.of("A", "B", "C")) {
                    names.put(tempData.user(run, n).getId(), n);
                }
                return new Setup(event.getId(), shop.getId(), names);
            });
            int i = 0;
            for (Long userId : s.names().keySet().stream().skip(1).toList()) {
                eventService.saveEventParticipation(s.eventId(), userId,
                    tempData.request("{\"paymentId\":\"" + run.paymentId("event-" + i++) + "\"}", EventParticipationRequest.class));
            }
            Notifications notes = new Notifications(s.names());
            notes.since();
            List<Step> steps = new ArrayList<>();
            steps.add(new Step("참가 3명", "정원 5명 이벤트에 A·B·C가 참가비 1,000원씩 내고 참가",
                eventFacts(s.eventId(), s.shopId()), notes.all()));

            jdbcTemplate.update("update shop_event_tb set deadline = ? where id = ?", LocalDateTime.now().minusMinutes(1), s.eventId());
            eventService.autoCloseShopEvents();
            steps.add(new Step("이벤트 마감 시간 지남", "정원이 다 차지 않은 채 마감 시간을 1분 전으로 당기고 이벤트 스케줄러 실행",
                eventFacts(s.eventId(), s.shopId()), notes.since()));
            return new Scenario("선착순 이벤트 마감 정산", steps);
        } finally {
            tempData.cleanup(run);
        }
    }

    private Map<String, String> eventFacts(Long eventId, Long shopId) {
        ShopEvent e = shopEventRepository.findById(eventId).orElseThrow();
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("이벤트 상태", "OPEN".equals(e.getStatus().name()) ? "모집 중" : "CLOSED".equals(e.getStatus().name()) ? "마감" : e.getStatus().name());
        facts.put("판매 수 / 정원", e.getCurrentCount() + " / " + e.getCapacity());
        facts.put("가게 정산", won(settled(shopId)));
        return facts;
    }

    // ---------- 공통 ----------

    private int refunded(String paymentId) {
        List<Integer> r = jdbcTemplate.queryForList("select refunded_amount from payment_tb where payment_id = ?", Integer.class, paymentId);
        return r.isEmpty() || r.get(0) == null ? 0 : r.get(0);
    }

    private int settled(Long shopId) {
        Integer sum = jdbcTemplate.queryForObject("select coalesce(sum(amount), 0) from settlement_history_tb where shop_id = ?", Integer.class, shopId);
        return sum == null ? 0 : sum;
    }

    private static String auctionStatus(String status) {
        return switch (status) {
            case "ACTIVE" -> "진행 중";
            case "ENDED_SUCCESS" -> "낙찰 (잔금 대기)";
            case "ENDED_FAIL" -> "유찰·낙찰 취소";
            case "CANCELLED" -> "취소";
            default -> status;
        };
    }

    private static String won(int amount) {
        return String.format("%,d원", amount);
    }

    private static String toTime(Object value) {
        LocalDateTime t = value instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) value;
        return t.format(TIME);
    }

    // 단계 사이에 새로 생긴 알림 (누가 받았는지 + 제목)
    private class Notifications {
        private final Map<Long, String> names;
        private final List<String> collected = new ArrayList<>();
        private long lastId = 0;

        Notifications(Map<Long, String> names) {
            this.names = names;
        }

        List<String> since() {
            String in = names.keySet().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            List<String> fresh = new ArrayList<>();
            for (Map<String, Object> row : jdbcTemplate.queryForList(
                "select id, receiver_id, title from notifications_tb where receiver_id in (" + in + ") and id > ? order by id", lastId)) {
                lastId = ((Number) row.get("id")).longValue();
                fresh.add(names.get(((Number) row.get("receiver_id")).longValue()) + ": " + row.get("title"));
            }
            collected.addAll(fresh);
            return fresh;
        }

        List<String> all() {
            return List.copyOf(collected);
        }
    }
}
