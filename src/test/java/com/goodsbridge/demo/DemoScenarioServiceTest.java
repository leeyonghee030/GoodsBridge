package com.goodsbridge.demo;

import com.goodsbridge.auction.AuctionRepository;
import com.goodsbridge.auction.AuctionScheduler;
import com.goodsbridge.auction.BidService;
import com.goodsbridge.event.EventService;
import com.goodsbridge.event.ShopEventRepository;
import com.goodsbridge.exchange.ExchangeItemRepository;
import com.goodsbridge.exchange.ExchangeRequestRepository;
import com.goodsbridge.exchange.ExchangeService;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 노쇼 정산·시간 빨리감기 시연이 실제 코드로 기획서 규칙대로 결과를 내고, 끝나면 임시 데이터를 지우는지
@SpringBootTest
@ActiveProfiles("test")
@Import(DemoFakePaymentConfig.class)
class DemoScenarioServiceTest {

    @Autowired private ExchangeService exchangeService;
    @Autowired private ExchangeItemRepository exchangeItemRepository;
    @Autowired private ExchangeRequestRepository exchangeRequestRepository;
    @Autowired private BidService bidService;
    @Autowired private com.goodsbridge.auction.AuctionService auctionService;
    @Autowired private com.goodsbridge.purchase.PurchaseService purchaseService;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private AuctionScheduler auctionScheduler;
    @Autowired private EventService eventService;
    @Autowired private ShopEventRepository shopEventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DemoTempData tempData;
    @Autowired private com.goodsbridge.shop.ShopSubscriptionBatchService subscriptionBatchService;

    @Test
    @DisplayName("빨리감기 구독 만료: D-3 알림 → 만료·가게 상품 판매 중지 → 이벤트 참가가 거절된다")
    void timeline_subscription_expiresAndPausesShop() {
        long usersBefore = userRepository.count();

        List<DemoTimelineService.Step> steps = timelineService().run("subscription").steps();

        assertThat(steps.get(0).facts()).containsEntry("구독 상태", "구독 중").containsEntry("판매 중 상품 (이벤트 상품 포함)", "3개");
        assertThat(steps.get(1).facts()).containsEntry("만료 임박 알림", "보냄");
        assertThat(steps.get(1).notifications()).contains("가게: [구독 만료 임박]");
        assertThat(steps.get(2).facts()).containsEntry("구독 상태", "만료")
            .containsEntry("판매 중 상품 (이벤트 상품 포함)", "0개").containsEntry("구독 만료로 중지된 상품", "3개");
        assertThat(steps.get(2).notifications()).contains("가게: [구독 만료]");
        assertThat(steps.get(3).facts().get("이벤트 참가 시도")).startsWith("거절").contains("구독");
        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    @Test
    @DisplayName("빨리감기 교환 제안 만료: 응답 기한이 지나면 제안 만료, B 보증금 전액 환불")
    void timeline_proposalExpired_refundsProposer() {
        List<DemoTimelineService.Step> steps = timelineService().run("proposalExpired").steps();

        assertThat(steps.get(0).facts()).containsEntry("교환 상태", "대기 중").containsEntry("B 보증금", "2,000원")
            .containsEntry("B 보증금 환불", "0원");
        assertThat(steps.get(1).facts()).containsEntry("교환 상태", "제안 만료").containsEntry("B 보증금 환불", "2,000원")
            .containsEntry("가게 정산", "0원");
        assertThat(steps.get(1).notifications()).contains("B: [교환 신청 만료]");
    }

    @Test
    @DisplayName("빨리감기 출석 체크 유예: 5분 안에는 정산하지 않고 되돌릴 수 있으며, 5분이 지나면 완료·정산된다")
    void timeline_checkInGrace_waitsFiveMinutesThenSettles() {
        List<DemoTimelineService.Step> steps = timelineService().run("checkInGrace").steps();

        assertThat(steps.get(1).facts()).containsEntry("교환 상태", "확정됨").containsEntry("B 출석", "왔음")
            .containsEntry("가게 정산", "0원");
        assertThat(steps.get(1).facts().get("5분 유예 시작")).isNotEqualTo("-");
        assertThat(steps.get(2).facts()).containsEntry("B 출석", "-").containsEntry("5분 유예 시작", "-");
        assertThat(steps.get(4).facts()).containsEntry("교환 상태", "완료됨")
            .containsEntry("A 보증금 환불", "4,500원").containsEntry("B 보증금 환불", "1,500원").containsEntry("가게 정산", "2,000원");
    }

    @Test
    @DisplayName("노쇼 정산: A 30,000원·B 8,000원이면 보증금 6,000·2,000원(최소 금액)으로 3케이스가 기획서대로 정산된다")
    void settlement_threeCases_matchSpec() {
        DemoSettlementService service = new DemoSettlementService(exchangeService, exchangeItemRepository,
            exchangeRequestRepository, transactionTemplate, jdbcTemplate, tempData);
        long usersBefore = userRepository.count();

        List<DemoSettlementService.Case> cases = service.run(30_000, 8_000).cases();

        // 둘 다 옴: 75% 환불, 가게 25%
        assertCase(cases.get(0), 6000, 4500, 2000, 1500, 2000);
        // B만 노쇼: A 전액 환불, B 전액 가게 몫
        assertCase(cases.get(1), 6000, 6000, 2000, 0, 2000);
        // 둘 다 노쇼: 50% 환불, 가게 50%
        assertCase(cases.get(2), 6000, 3000, 2000, 1000, 4000);
        assertThat(cases).allMatch(DemoSettlementService.Case::matchesSpec);
        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    private void assertCase(DemoSettlementService.Case c, int depositA, int refundA, int depositB, int refundB, int shopTotal) {
        assertThat(c.a().deposit()).isEqualTo(depositA);
        assertThat(c.a().refund()).isEqualTo(refundA);
        assertThat(c.b().deposit()).isEqualTo(depositB);
        assertThat(c.b().refund()).isEqualTo(refundB);
        assertThat(c.shopTotal()).isEqualTo(shopTotal);
    }

    @Test
    @DisplayName("빨리감기: 경매는 낙찰·패찰 환불 → 잔금 미납으로 보증금이 가게 몫, 이벤트는 마감·참가비 정산까지 진행된다")
    void timeline_runsSchedulersStepByStep() {
        DemoTimelineService service = timelineService();
        long usersBefore = userRepository.count();

        List<DemoTimelineService.Step> auction = service.run("auction").steps();
        assertThat(auction.get(0).facts()).containsEntry("경매 상태", "진행 중").containsEntry("최고 입찰자", "C");
        assertThat(auction.get(1).facts()).containsEntry("경매 상태", "낙찰 (잔금 대기)")
            .containsEntry("A 보증금 환불", "1,000원").containsEntry("B 보증금 환불", "1,000원")
            .containsEntry("C 보증금 환불", "0원");
        assertThat(auction.get(1).notifications()).isNotEmpty();
        assertThat(auction.get(2).facts()).containsEntry("경매 상태", "유찰·낙찰 취소").containsEntry("가게 정산", "1,000원");

        List<DemoTimelineService.Step> event = service.run("event").steps();
        assertThat(event.get(0).facts()).containsEntry("이벤트 상태", "모집 중").containsEntry("판매 수 / 정원", "3 / 5");
        assertThat(event.get(1).facts()).containsEntry("이벤트 상태", "마감").containsEntry("가게 정산", "3,000원");

        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    @Test
    @DisplayName("빨리감기 낙찰 성공: 잔금 결제(배송지) → 발송 → 배송 완료 → 구매 확정까지 가고 낙찰가 전체가 가게 정산된다")
    void timeline_auctionPaid_goesThroughDeliveryToConfirm() {
        long usersBefore = userRepository.count();

        List<DemoTimelineService.Step> steps = timelineService().run("auctionPaid").steps();

        assertThat(steps).extracting(DemoTimelineService.Step::title)
            .containsExactly("입찰 3건", "경매 마감 시간 지남", "C가 배송지를 적고 잔금 결제", "가게가 발송", "배송 완료", "C가 구매 확정");
        assertThat(steps.get(2).facts()).containsEntry("경매 상태", "낙찰 (잔금 결제 완료)")
            .containsEntry("배송지", "서울시 성동구 시연로 12, 101호").containsEntry("C 잔금 납부", "14,000원");
        assertThat(steps.get(3).facts()).containsEntry("배송 상태", "배송중").containsEntry("송장 번호", "DEMO-1120-0001");
        assertThat(steps.get(4).facts()).containsEntry("배송 상태", "배송 완료");
        assertThat(steps.get(5).facts()).containsEntry("구매 상태", "구매 확정").containsEntry("가게 정산", "15,000원");
        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    private DemoTimelineService timelineService() {
        return new DemoTimelineService(bidService, auctionService, purchaseService, auctionRepository, auctionScheduler,
            eventService, shopEventRepository, subscriptionBatchService, exchangeService, exchangeItemRepository,
            exchangeRequestRepository, transactionTemplate, jdbcTemplate, tempData);
    }
}
