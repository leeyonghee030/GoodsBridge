package com.goodsbridge.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PortOnePaymentService {
    private final PortOneClient portOneClient;

    public PaymentResponse.PortOnePayment getPayment(String paymentId) {
        return portOneClient.getPayment(paymentId);
    }

    public PaymentResponse.PortOneCancel cancelPayment(String paymentId, int amount, String reason) {
        return portOneClient.cancelPayment(paymentId, amount, reason);
    }
}
