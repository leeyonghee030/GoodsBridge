package com.goodsbridge.demo;

import com.goodsbridge.auction.AuctionRepository;
import com.goodsbridge.auction.BidRepository;
import com.goodsbridge.auction.BidService;
import com.goodsbridge.event.EventService;
import com.goodsbridge.event.ShopEventRepository;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

// 데모 동시성 시연: 락 있음은 규칙대로만 성공, 락 없음은 규칙을 넘겨 성공하고, 끝나면 임시 데이터가 남지 않는다
@SpringBootTest
@ActiveProfiles("test")
@Import(DemoFakePaymentConfig.class)
class DemoConcurrencyServiceTest {

    @Autowired private EventService eventService;
    @Autowired private BidService bidService;
    @Autowired private ShopEventRepository shopEventRepository;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private BidRepository bidRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PaymentService paymentService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private DemoTempData tempData;
    @Autowired private JdbcTemplate jdbcTemplate;

    private DemoConcurrencyService service;

    @BeforeEach
    void setUp() {
        // 테스트에서는 데모 모드가 꺼져 있어 빈이 없으므로 직접 만든다
        service = new DemoConcurrencyService(eventService, bidService, shopEventRepository, auctionRepository,
            bidRepository, userRepository, paymentService, transactionTemplate, tempData);
    }

    @Test
    @DisplayName("이벤트: 정원 5, 20명 동시 신청이면 락 있음은 5명만 성공하고 락 없음은 정원을 넘긴다. 임시 데이터는 지워진다")
    void event_comparesLockAndNoLock_andCleansUp() {
        long usersBefore = userRepository.count();
        long eventsBefore = shopEventRepository.count();

        DemoConcurrencyService.Result result = service.run(5, 20);

        assertThat(result.withLock().success()).isEqualTo(5);
        assertThat(result.withLock().fail()).isEqualTo(15);
        assertThat(result.withLock().recordedCount()).isEqualTo(5);
        assertThat(result.withLock().status()).isEqualTo("CLOSED");
        assertThat(result.withoutLock().success()).isGreaterThan(5);

        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(shopEventRepository.count()).isEqualTo(eventsBefore);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from payment_tb where payment_id like 'demo-pay-%'", Long.class)).isZero();
    }

    @Test
    @DisplayName("이벤트: 최대값(정원 20, 50명 동시)에서도 연결 풀이 바닥나지 않고 정원만큼만 빠르게 성공한다")
    void event_atMaxUsers_doesNotExhaustConnectionPool() {
        DemoConcurrencyService.Result result = service.run(DemoConcurrencyService.MAX_CAPACITY, DemoConcurrencyService.MAX_USERS);

        assertThat(result.withLock().success()).isEqualTo(DemoConcurrencyService.MAX_CAPACITY);
        assertThat(result.withLock().fail()).isEqualTo(DemoConcurrencyService.MAX_USERS - DemoConcurrencyService.MAX_CAPACITY);
        // 연결 풀이 바닥나면 연결 대기 시간(30초)까지 멈췄다가 실패한다
        assertThat(result.withLock().elapsedMillis()).isLessThan(10_000);
    }

    @Test
    @DisplayName("경매: 20명이 같은 금액으로 동시에 첫 입찰하면 락 있음은 1명만 성공하고 락 없음은 여러 명이 성공한다")
    void auction_comparesLockAndNoLock_andCleansUp() {
        long usersBefore = userRepository.count();
        long auctionsBefore = auctionRepository.count();

        DemoConcurrencyService.AuctionResult result = service.runAuction(20);

        assertThat(result.withLock().success()).isEqualTo(1);
        assertThat(result.withLock().fail()).isEqualTo(19);
        assertThat(result.withLock().bidCount()).isEqualTo(1);
        assertThat(result.withoutLock().success()).isGreaterThan(1);
        assertThat(result.withoutLock().bidCount()).isEqualTo(result.withoutLock().success());

        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(auctionRepository.count()).isEqualTo(auctionsBefore);
    }

    @Test
    @DisplayName("경매: 최대값(50명 동시 입찰)에서도 연결 풀이 바닥나지 않고 1명만 빠르게 성공한다")
    void auction_atMaxUsers_doesNotExhaustConnectionPool() {
        DemoConcurrencyService.AuctionResult result = service.runAuction(DemoConcurrencyService.MAX_USERS);

        assertThat(result.withLock().success()).isEqualTo(1);
        assertThat(result.withLock().fail()).isEqualTo(DemoConcurrencyService.MAX_USERS - 1);
        assertThat(result.withLock().elapsedMillis()).isLessThan(10_000);
    }
}
