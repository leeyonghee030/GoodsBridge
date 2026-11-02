package com.goodsbridge.payment;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaymentViewController {

    @GetMapping("/test/payment")
    public String index() {
        return "payment/index";
    }
}
