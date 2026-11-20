package com.goodsbridge.auction;

import com.goodsbridge.demo.DemoTempData;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.SaleType;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentResponse;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// 입찰이 동시에 여러 건 성공해도(서로 다른 경매) 알림 저장 때문에 DB 연결 풀이 바닥나 멈추지 않는지.
// 알림을 트랜잭션 안에서 발행하면 커밋 직후 알림 저장이 쓰던 연결을 쥔 채 연결을 하나 더 받아, 풀(10개)보다 많이 동시에 성공하면 모두 멈춘다.
@SpringBootTest
@ActiveProfiles("test")
class BidConnectionPoolTest {

    private static final int BIDDERS = 20;

    @TestConfiguration
    static class FakePaymentConfig {
        @Bean
        @Primary
        PaymentService fakePaymentService() {
            return new PaymentService() {
                @Override public String verify(String paymentId, int expectedAmount, PaymentPurpose purpose, Long userId) { return paymentId; }
                @Override public List<PaymentResponse.DetailDto> findMine(Long userId) { return List.of(); }
                @Override public void refund(String paymentId, int amount, String reason) {}
                @Override public void release(String paymentId) {}
            };
        }
    }

    @Autowired private BidService bidService;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private DemoTempData tempData;

    @Test
    void manySuccessfulBidsAtOnce_doNotExhaustConnectionPool() throws Exception {
        DemoTempData.Run run = tempData.start();
        try {
            List<long[]> pairs = transactionTemplate.execute(status -> {
                User owner = tempData.user(run, "owner");
                Shop shop = tempData.shop(run, owner);
                List<long[]> list = new ArrayList<>();
                for (int i = 0; i < BIDDERS; i++) {
                    Item item = tempData.item(run, owner, SaleType.AUCTION, 10_000, "풀 테스트 " + i);
                    Auction auction = auctionRepository.save(Auction.builder().shop(shop).item(item)
                        .startPrice(10_000).depositAmount(1_000).deadline(LocalDateTime.now().plusDays(1)).build());
                    list.add(new long[]{auction.getId(), tempData.user(run, "u" + i).getId()});
                }
                return list;
            });

            ExecutorService executor = Executors.newFixedThreadPool(BIDDERS);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(BIDDERS);
            AtomicInteger success = new AtomicInteger();
            for (long[] pair : pairs) {
                executor.submit(() -> {
                    try {
                        start.await();
                        bidService.placeBid(pair[0], pair[1], new BidRequest(10_000, run.paymentId("pool-" + pair[1])));
                        success.incrementAndGet();
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }
            long begin = System.currentTimeMillis();
            start.countDown();
            done.await(60, TimeUnit.SECONDS);
            executor.shutdownNow();

            assertThat(success.get()).isEqualTo(BIDDERS);
            // 연결 풀이 바닥나면 연결 대기 시간(30초)까지 멈췄다가 실패한다
            assertThat(System.currentTimeMillis() - begin).isLessThan(10_000);
        } finally {
            tempData.cleanup(run);
        }
    }
}
