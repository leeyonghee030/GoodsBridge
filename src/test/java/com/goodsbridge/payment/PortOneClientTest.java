package com.goodsbridge.payment;

import com.goodsbridge._core.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

// paymentId 가 URL 경로에 그대로 붙기 때문에, 외부 호출 전에 형식을 검증하는지 확인한다.
class PortOneClientTest {

    private final PortOneClient portOneClient = new PortOneClient();

    @Test
    void getPayment_rejectsPathOrQueryInjectionBeforeCallingPortOne() {
        for (String invalid : List.of("../payments", "a/b", "a?x=1", "a b", "", " ", "a".repeat(101))) {
            assertThrows(BadRequestException.class, () -> portOneClient.getPayment(invalid), invalid);
        }
        assertThrows(BadRequestException.class, () -> portOneClient.getPayment(null));
    }

    @Test
    void cancelPayment_rejectsInvalidPaymentIdBeforeCallingPortOne() {
        assertThrows(BadRequestException.class, () -> portOneClient.cancelPayment("../x", 1_000, "사유"));
        assertThrows(BadRequestException.class, () -> portOneClient.cancelPayment("a?b=1", 1_000, "사유"));
    }
}
