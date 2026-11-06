package com.goodsbridge.shop;

import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestPaymentConfig.class)
class ShopSubscriptionConcurrencyTest {

    @Autowired
    private ShopSubscriptionService subscriptionService;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopSubscriptionRepository subscriptionRepository;

    @AfterEach
    void tearDown() {
        subscriptionRepository.deleteAllInBatch();
        shopRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("동일한 paymentId로 다중 스레드가 동시에 구독 생성을 요청해도 단 1건만 성공하고 나머지는 방어된다.")
    void concurrentSubscriptionCreation_preventsDuplicatePayment() throws InterruptedException {
        // given: 테스트용 유저와 샵 생성
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

        // 💡 1. 멀티스레드 진입 전, 모든 스레드가 공유할 단 하나의 고유 paymentId 생성
        String sharedPaymentId = "PAY_CONCURRENT_" + UUID.randomUUID();

        // 💡 2. 요청 DTO에 sharedPaymentId 세팅
        ShopSubscriptionRequest request = new ShopSubscriptionRequest();
        request.setPaymentId(sharedPaymentId);
        request.setDays(30);
        request.setAmount(30000);

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 5개의 스레드가 동일한 request(동일한 paymentId)로 동시에 구독 생성 요청
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                String threadName = Thread.currentThread().getName();
                try {
                    subscriptionService.create(owner.getId(), shop.getId(), request);
                    successCount.incrementAndGet();
                    System.out.println("🟢 [" + threadName + "] 구독 생성 성공!");
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("🔴 [" + threadName + "] 동시성 방어 성공 (예외 발생): " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then: 정확히 1건만 성공하고, 나머지 4건은 예외로 방어되었는지 검증
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);

        long activeSubscriptionCount = subscriptionRepository.findAll().stream()
            .filter(sub -> sub.getShopId().equals(shop.getId()) && sub.getStatus() == ShopSubscriptionStatus.ACTIVE)
            .count();
        assertThat(activeSubscriptionCount).isEqualTo(1);
    }
}
