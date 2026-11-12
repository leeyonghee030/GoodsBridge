
package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
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

    @Test
    @DisplayName("동일한 결제 ID로 동시에 구독 요청 시 한 건만 성공하고 나머지는 중복 예외로 방어한다")
    void concurrentSubscriptionCreationTest() throws InterruptedException {

        // given
        User savedUser = userRepository.save(
            User.builder()
                .email("test_owner@goodsbridge.com")
                .password("encoded_password")
                .nickname("테스트사장님")
                .loginType(LoginType.EMAIL)
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

        String sharedPaymentId =
            "PAYMENT_DUPLICATE_TEST_" + System.currentTimeMillis();

        int threadCount = 5;

        ExecutorService executorService =
            Executors.newFixedThreadPool(threadCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        List<Future<ShopTestResult>> futures = new ArrayList<>();

        int successCount = 0;
        int duplicateExceptionCount = 0;

        List<Throwable> unexpectedExceptions = new ArrayList<>();

        try {

            // when: 여러 스레드에서 동시에 구독 요청
            for (int i = 0; i < threadCount; i++) {

                futures.add(executorService.submit(() -> {

                    ShopSubscriptionRequest request =
                        new ShopSubscriptionRequest();

                    request.setPaymentId(sharedPaymentId);
                    request.setDays(30);
                    request.setAmount(30000);

                    try {
                        startLatch.await();

                        subscriptionService.create(
                            testUserId,
                            testShopId,
                            request
                        );

                        return new ShopTestResult(true, null);

                    } catch (Exception e) {
                        return new ShopTestResult(false, e);

                    } finally {
                        endLatch.countDown();
                    }
                }));
            }

            // 모든 스레드 동시 시작
            startLatch.countDown();

            boolean finished =
                endLatch.await(10, TimeUnit.SECONDS);

            assertThat(finished)
                .as("모든 동시성 테스트 스레드가 제한 시간 내 종료되어야 한다.")
                .isTrue();

            // 모든 Future 결과 수집
            for (Future<ShopTestResult> future : futures) {

                try {
                    ShopTestResult result = future.get();

                    if (result.isSuccess()) {
                        successCount++;

                    } else {
                        Throwable ex = result.getException();

                        if (isDuplicateException(ex)) {
                            duplicateExceptionCount++;
                        } else {
                            unexpectedExceptions.add(ex);
                        }
                    }

                } catch (ExecutionException e) {
                    unexpectedExceptions.add(e.getCause());
                }
            }

            // then: 동시성 결과 검증
            System.out.println(
                "=== [동시성 테스트 결과] 성공: "
                    + successCount
                    + ", 중복 방어: "
                    + duplicateExceptionCount
            );

            assertThat(unexpectedExceptions)
                .as("예상하지 못한 예외가 발생하면 안 된다.")
                .isEmpty();

            assertThat(successCount).isEqualTo(1);

            assertThat(duplicateExceptionCount)
                .isEqualTo(threadCount - 1);

            // 실제 DB에도 구독 이력이 한 건만 남아 있어야 한다.
            List<ShopSubscription> savedSubscriptions =
                subscriptionRepository.findAll()
                    .stream()
                    .filter(subscription ->
                        testShopId.equals(subscription.getShopId()))
                    .toList();

            assertThat(savedSubscriptions).hasSize(1);

        } finally {

            // 모든 작업 종료 후 테스트 데이터 정리
            executorService.shutdownNow();

            boolean terminated =
                executorService.awaitTermination(10, TimeUnit.SECONDS);

            if (!terminated) {
                System.err.println("[테스트 스레드 종료 실패]");
            }

            try {
                // 해당 가게에 속한 구독 이력 전체 삭제
                List<ShopSubscription> subscriptions =
                    subscriptionRepository.findAll()
                        .stream()
                        .filter(subscription ->
                            testShopId.equals(subscription.getShopId()))
                        .toList();

                subscriptionRepository.deleteAll(subscriptions);

                shopRepository.delete(savedShop);
                userRepository.delete(savedUser);

            } catch (Exception e) {
                System.err.println(
                    "[테스트 데이터 정리 실패] " + e.getMessage()
                );
                throw e;
            }
        }
    }

    // 중복 결제 예외인지 확인
    private boolean isDuplicateException(Throwable exception) {

        Throwable current = exception;

        while (current != null) {

            if (current instanceof BadRequestException
                || current instanceof DataIntegrityViolationException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
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
