package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;//
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestPaymentConfig.class) // 💡 3. 가짜 결제 설정 추가하여 실제 외부 PG 검증 우회
class ShopSubscriptionConcurrencyTest {

    @Autowired
    private ShopSubscriptionService subscriptionService;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopSubscriptionRepository subscriptionRepository; // 💡 6. 구독 데이터 정리를 위해 주입

    @Test
    @DisplayName("동일한 결제 ID(paymentId)로 동시에 여러 번 구독 요청 시, 단 1건만 성공하고 나머지는 중복 예외로 방어되어야 한다.")
    void concurrentSubscriptionCreationTest() throws InterruptedException {
        // given
        User savedUser = userRepository.save(
            User.builder()
                .email("test_owner@goodsbridge.com")
                .password("encoded_password")
                .nickname("테스트사장님")
                .build()
        );
        Long testUserId = savedUser.getId();

        Shop savedShop = shopRepository.save(
            Shop.builder()
                .ownerUserId(testUserId)
                .name("동시성 테스트 팝업스토어")
                .region("부산")
                .address("해운대구 우동 123")
                .status(ShopStatus.APPROVED)
                .build()
        );
        Long testShopId = savedShop.getId();

        String sharedPaymentId = "PAYMENT_DUPLICATE_TEST_" + System.currentTimeMillis();
        // 💡 3. 사전 결제 데이터 저장 코드는 제거 (가짜 결제 환경에서 검증 통과)

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        List<Future<ShopTestResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(executorService.submit(() -> {
                    ShopSubscriptionRequest request = new ShopSubscriptionRequest();
                    request.setPaymentId(sharedPaymentId);
                    request.setDays(30);
                    request.setAmount(30000);

                    try {
                        startLatch.await();
                        subscriptionService.create(testUserId, testShopId, request);
                        return new ShopTestResult(true, null);
                    } catch (Exception e) {
                        return new ShopTestResult(false, e);
                    } finally {
                        endLatch.countDown();
                    }
                }));
            }

            startLatch.countDown();

            boolean finished = endLatch.await(10, TimeUnit.SECONDS);
            assertThat(finished).isTrue();

        } finally {
            executorService.shutdownNow();

            // 💡 6. 테스트 데이터 정리 시 구독(Subscription) 및 예외 로깅 보완
            try {
                subscriptionRepository.findByShopId(testShopId).ifPresent(subscriptionRepository::delete);
                shopRepository.delete(savedShop);
                userRepository.delete(savedUser);
            } catch (Exception e) {
                System.err.println("[테스트 데이터 정리 실패] " + e.getMessage());
            }
        }

        // when & then
        int successCount = 0;
        int duplicateExceptionCount = 0;
        List<Throwable> unexpectedExceptions = new ArrayList<>();

        for (Future<ShopTestResult> future : futures) {
            try {
                ShopTestResult result = future.get();
                if (result.isSuccess()) {
                    successCount++;
                } else {
                    Throwable ex = result.getException();
                    if (ex instanceof BadRequestException || ex instanceof DataIntegrityViolationException ||
                        (ex.getCause() != null && (ex.getCause() instanceof BadRequestException || ex.getCause() instanceof DataIntegrityViolationException))) {
                        duplicateExceptionCount++;
                    } else {
                        unexpectedExceptions.add(ex);
                    }
                }
            } catch (ExecutionException e) {
                unexpectedExceptions.add(e.getCause());
            }
        }

        System.out.println("=== [동시성 테스트 결과] 성공 횟수: " + successCount + ", 중복 방어 횟수: " + duplicateExceptionCount);
        if (!unexpectedExceptions.isEmpty()) {
            System.err.println("=== [예상치 못한 예외 발생 목록] ===");
            for (Throwable ex : unexpectedExceptions) {
                ex.printStackTrace();
            }
        }

        assertThat(unexpectedExceptions).isEmpty();
        assertThat(successCount).isEqualTo(1);
        assertThat(duplicateExceptionCount).isEqualTo(threadCount - 1);
    }

    private static class ShopTestResult {
        private final boolean success;
        private final Exception exception;

        public ShopTestResult(boolean success, Exception exception) {
            this.success = success;
            this.exception = exception;
        }

        public boolean isSuccess() {
            return success;
        }

        public Exception getException() {
            return exception;
        }
    }
}
