package com.goodsbridge.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodsbridge.event.dto.EventParticipationRequest;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.SaleType;
import com.goodsbridge.item.Category;
import com.goodsbridge.item.CategoryJpaRepository;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentResponse;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.shop.ShopStatus;
import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// ★ 이벤트 선착순 참가 동시성 테스트 (CLAUDE.md 테스트 우선순위 2위) — 삭제하지 말고 유지
@SpringBootTest
@ActiveProfiles("test")
class EventParticipationConcurrencyTest {

    // 실제 PortOne 네트워크 호출 없이 동시성(락/정원체크) 로직만 검증하기 위한 가짜 결제 서비스
    @TestConfiguration
    static class FakePaymentServiceConfig {
        @Bean
        @Primary
        PaymentService fakePaymentService() {
            return new PaymentService() {
                @Override
                public String verify(String paymentId, int expectedAmount, PaymentPurpose purpose, Long userId) {
                    return paymentId;
                }

                @Override
                public List<PaymentResponse.DetailDto> findMine(Long userId) {
                    return List.of();
                }

                @Override
                public void refund(String paymentId, int amount, String reason) {
                }

                @Override
                public void release(String paymentId) {
                }
            };
        }
    }

    @Autowired private EventService eventService;
    @Autowired private CategoryJpaRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private ShopEventRepository shopEventRepository;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("정원 5명인 이벤트에 10명이 동시에 참가 신청해도 딱 5명만 성공한다")
    void concurrentJoin_neverExceedsCapacity() throws Exception {
        int capacity = 5;
        int threadCount = 10;

        Category category = categoryRepository.save(Category.builder().name("동시성테스트-" + UUID.randomUUID()).build());

        User owner = userRepository.save(new User(
            "owner-" + UUID.randomUUID() + "@test.com", "pw", "사장-" + UUID.randomUUID(), LoginType.EMAIL, null));

        Shop shop = shopRepository.save(Shop.builder()
            .ownerUserId(owner.getId())
            .name("동시성테스트 가게")
            .region("서울")
            .address("서울 어딘가")
            .status(ShopStatus.APPROVED)
            .build());

        Item item = itemRepository.save(Item.builder()
            .sellerUserId(owner.getId())
            .category(category)
            .title("동시성테스트 상품")
            .price(10000)
            .saleType(SaleType.EVENT)
            .build());

        ShopEvent shopEvent = shopEventRepository.save(ShopEvent.builder()
            .shop(shop)
            .item(item)
            .capacity(capacity)
            .feeAmount(1000)
            .deadline(LocalDateTime.now().plusDays(1))
            .build());

        List<User> participants = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            participants.add(userRepository.save(new User(
                "participant-" + UUID.randomUUID() + "@test.com", "pw", "참가자-" + UUID.randomUUID(), LoginType.EMAIL, null)));
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (User participant : participants) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    EventParticipationRequest request = newRequest("pay-" + participant.getId());
                    eventService.saveEventParticipation(shopEvent.getId(), participant.getId(), request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(capacity);
        assertThat(failCount.get()).isEqualTo(threadCount - capacity);

        ShopEvent updated = shopEventRepository.findById(shopEvent.getId()).orElseThrow();
        assertThat(updated.getCurrentCount()).isEqualTo(capacity);
        assertThat(updated.getStatus()).isEqualTo(ShopEventStatus.CLOSED);
    }

    private EventParticipationRequest newRequest(String paymentId) throws Exception {
        return OBJECT_MAPPER.readValue("{\"paymentId\":\"" + paymentId + "\"}", EventParticipationRequest.class);
    }
}
