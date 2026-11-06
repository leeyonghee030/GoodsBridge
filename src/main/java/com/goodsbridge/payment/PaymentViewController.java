package com.goodsbridge.payment;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class PaymentViewController {
    private final PaymentService paymentService;

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.channel-key}")
    private String channelKey;

    @GetMapping("/test/payment")
    public String index(Model model) {
        model.addAttribute("portoneStoreId", storeId);
        model.addAttribute("portoneChannelKey", channelKey);
        return "payment/index";
    }

    // 내 전체 결제 내역 조회
    @GetMapping("/api/payments/me")
    public ResponseEntity<List<PaymentResponse.DetailDto>> myPayments(){
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(paymentService.findMine(userId));
    }
}
