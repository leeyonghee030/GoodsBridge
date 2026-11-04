package com.goodsbridge.payment;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class PaymentViewController {
    private final PaymentService paymentService;

    @GetMapping("/test/payment")
    public String index() {
        return "payment/index";
    }

    // 내 전체 결제 내역 조회
    @GetMapping("/api/payments/me")
    public ResponseEntity<List<PaymentResponse.DetailDto>> myPayments(){
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(paymentService.findMine(userId));
    }
}
