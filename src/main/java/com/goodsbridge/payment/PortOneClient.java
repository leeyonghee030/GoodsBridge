package com.goodsbridge.payment;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Getter
@Component
public class PortOneClient {
    @Value("${portone.store-id}")
    private String storeId;
    @Value("${portone.api-secret}")
    private String apiSecret;

    private static final String PAYMENT_URL = "https://api.portone.io/payments";
    private static final RestTemplate restTemplate = new RestTemplate();

    // 포트원 서버에 결제 단건 조회
    public PaymentResponse.PortOnePayment getPayment(String paymentId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "PortOne " + apiSecret); // PortOne 뒤 반드 시 공백 한 칸
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<PaymentResponse.PortOnePayment> response = restTemplate.exchange(PAYMENT_URL + "/" + paymentId,
            HttpMethod.GET,
            request,
            PaymentResponse.PortOnePayment.class);
        return response.getBody();
    }

}
