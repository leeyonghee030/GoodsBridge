package com.goodsbridge.shop;

import com.goodsbridge.payment.PaymentService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
class TestPaymentConfig {

    @Bean
    @Primary
    public PaymentService fakePaymentService() {
        return new FakePaymentService();
    }
}
