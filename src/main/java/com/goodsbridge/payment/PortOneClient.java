package com.goodsbridge.payment;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.InternalServerException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Getter
@Component
public class PortOneClient {

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.api-secret}")
    private String apiSecret;

    private static final String PAYMENT_URL = "https://api.portone.io/payments";

    private final RestTemplate restTemplate = new RestTemplate();

    // PortOne 결제 단건 조회
    public PaymentResponse.PortOnePayment getPayment(String paymentId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, "PortOne " + apiSecret);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<PaymentResponse.PortOnePayment> response = restTemplate.exchange(
                    PAYMENT_URL + "/" + paymentId,
                    HttpMethod.GET,
                    request,
                    PaymentResponse.PortOnePayment.class
                );
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new BadRequestException("결제를 찾을 수 없습니다.");
        } catch (HttpClientErrorException e) {
            log.error(
                "PortOne 조회 실패 status={}, body={}",
                e.getStatusCode(),
                e.getResponseBodyAsString()
            );
            throw new BadRequestException("PortOne 결제 조회에 실패했습니다.");
        } catch (ResourceAccessException e) {
            throw new InternalServerException("결제 서버에 연결할 수 없습니다.");
        } catch (RestClientException e) {
            throw new InternalServerException("결제 서버 통신 중 오류가 발생했습니다.");
        }
    }

    // 포트원 서버에 결제 취소 요청
    public PaymentResponse.PortOneCancel cancelPayment(String paymentId, int amount) {
        return cancelPayment(paymentId, amount, "관리자 환불 승인");
    }

    public PaymentResponse.PortOneCancel cancelPayment(String paymentId, int amount, String reason) {
        if (paymentId == null || paymentId.isBlank() || amount <= 0) {
            throw new BadRequestException("결제 취소 요청 값이 올바르지 않습니다.");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, "PortOne " + apiSecret);
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of(
                "amount", amount,
                "reason", reason == null || reason.isBlank() ? "관리자 환불 승인" : reason
            );
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<PaymentResponse.PortOneCancel> response =
                restTemplate.exchange(
                    PAYMENT_URL + "/" + paymentId + "/cancel",
                    HttpMethod.POST,
                    request,
                    PaymentResponse.PortOneCancel.class
                );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            throw new BadRequestException("PortOne 결제 취소에 실패했습니다.");
        } catch (ResourceAccessException e) {
            throw new InternalServerException("결제 서버에 연결할 수 없습니다.");
        } catch (RestClientException e) {
            throw new InternalServerException("결제 취소 통신 중 오류가 발생했습니다.");
        }
    }
}
