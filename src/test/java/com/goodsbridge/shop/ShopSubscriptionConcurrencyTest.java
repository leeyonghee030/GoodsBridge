package com.goodsbridge.shop;

import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestPaymentConfig.class) // Mockito 없이 순수 Fake 빈 주입
class ShopSubscriptionConcurrencyTest {

    @Autowired
    private ShopSubscriptionService subscriptionService;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopSubscriptionRepository subscriptionRepository;

    @Test
    @DisplayName("동일한 paymentId로 다중 스레드가 동시에 구독 생성을 요청해도 단 1건만 성공하고 나머지는 방어된다.")
    void concurrentSubscriptionCreation_preventsDuplicatePayment() throws InterruptedException {
        // given
        User owner = userRepository.save(User.builder()
            .email("concurrent_owner@goodsbridge.com")
            .password("password123!")
            .nickname("동시성사장님")
            .loginType(LoginType.EMAIL)
            .build());

        Shop shop = shopRepository.save(Shop.builder()
            .ownerUserId(owner.getId())
            .name("동시성테스트샵")
            .region("부산")
            .address("해운대구")
            .status(ShopStatus.APPROVED)
            .build());

        String sharedPaymentId = "PAY_CONCURRENT_DUPLICATE_001";

        ShopSubscriptionRequest request = new ShopSubscriptionRequest();
        // request.setDays(30);
        // request.setPaymentId(sharedPaymentId);
        // request.setAmount(30000);

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    subscriptionService.create(owner.getId(), shop.getId(), request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);

        long activeSubscriptionCount = subscriptionRepository.findAll().stream()
            .filter(sub -> sub.getShopId().equals(shop.getId()) && sub.getStatus() == ShopSubscriptionStatus.ACTIVE)
            .count();
        assertThat(activeSubscriptionCount).isEqualTo(1);
    }
}
