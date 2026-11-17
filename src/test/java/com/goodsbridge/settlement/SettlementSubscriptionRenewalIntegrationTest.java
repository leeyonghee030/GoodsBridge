package com.goodsbridge.settlement;

import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.shop.ShopSubscriptionRequest;
import com.goodsbridge.shop.ShopSubscriptionService;
import com.goodsbridge.shop.ShopStatus;
import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(SettlementSubscriptionRenewalIntegrationTest.TestConfig.class)
@Transactional
class SettlementSubscriptionRenewalIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired ShopRepository shopRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired SettlementHistoryRepository settlementHistoryRepository;
    @Autowired SettlementHistoryService settlementHistoryService;
    @Autowired ShopSubscriptionService shopSubscriptionService;

    @Test
    void summarizeByShop_includesPreviousSubscriptionPaymentAfterRenewal() {
        User owner = userRepository.save(new User(
            "renewal-owner-" + System.nanoTime() + "@test.com",
            "password",
            "renewal-owner-" + System.nanoTime(),
            LoginType.EMAIL,
            null
        ));
        Shop shop = shopRepository.save(new Shop(
            owner.getId(), "연장 테스트 가게", "서울", "서울시", ShopStatus.APPROVED
        ));

        Payment initialSubscription = paymentRepository.save(payment(
            "renewal-initial-" + System.nanoTime(), owner, 365_000, PaymentPurpose.SHOP_SUBSCRIPTION
        ));
        Payment renewedSubscription = paymentRepository.save(payment(
            "renewal-second-" + System.nanoTime(), owner, 30_000, PaymentPurpose.SHOP_SUBSCRIPTION
        ));

        ShopSubscriptionRequest initialRequest = subscriptionRequest(
            initialSubscription.getPaymentId(), 365, 365_000
        );
        ShopSubscriptionRequest renewalRequest = subscriptionRequest(
            renewedSubscription.getPaymentId(), 30, 30_000
        );
        shopSubscriptionService.create(owner.getId(), shop.getId(), initialRequest);
        shopSubscriptionService.create(owner.getId(), shop.getId(), renewalRequest);

        Payment sale = paymentRepository.save(payment(
            "renewal-sale-" + System.nanoTime(), owner, 355_500, PaymentPurpose.PURCHASE
        ));
        settlementHistoryRepository.save(new SettlementHistory(
            shop, sale, 355_500, SettlementHistoryType.PURCHASE_FEE
        ));

        LocalDate today = LocalDate.now();
        SettlementHistoryResponse.SummaryDto summary = settlementHistoryService.summarizeByShop(
            shop.getId(), owner.getId(), null, today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1).minusDays(1)
        );

        assertThat(summary.getGrossRevenue()).isEqualTo(355_500);
        assertThat(summary.getSubscriptionExpense()).isEqualTo(395_000);
        assertThat(summary.getNetRevenue()).isEqualTo(-39_500);
    }

    private static Payment payment(String paymentId, User user, int amount, PaymentPurpose purpose) {
        return new Payment(paymentId, "pg-" + paymentId, user, amount, purpose);
    }

    private static ShopSubscriptionRequest subscriptionRequest(String paymentId, int days, int amount) {
        ShopSubscriptionRequest request = new ShopSubscriptionRequest();
        request.setPaymentId(paymentId);
        request.setDays(days);
        request.setAmount(amount);
        return request;
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        PaymentService paymentService() {
            return mock(PaymentService.class);
        }
    }
}
