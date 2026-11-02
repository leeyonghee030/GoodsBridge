package com.goodsbridge.exchange;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ExchangeViewController {

    @GetMapping("/test/exchange")
    public String index() {
        return "exchange/index";
    }
}
