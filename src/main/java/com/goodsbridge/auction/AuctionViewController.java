package com.goodsbridge.auction;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuctionViewController {

    @GetMapping("/test/auction")
    public String index() {
        return "auction/index";
    }
}
