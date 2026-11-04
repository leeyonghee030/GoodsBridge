package com.goodsbridge.payment;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.InternalServerException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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
            throw new BadRequestException("PortOne 결제 조회에 실패했습니다.");
        } catch (ResourceAccessException e) {
            throw new InternalServerException("결제 서버에 연결할 수 없습니다.");
        } catch (RestClientException e) {
            throw new InternalServerException("결제 서버 통신 중 오류가 발생했습니다.");
        }
    }
}
