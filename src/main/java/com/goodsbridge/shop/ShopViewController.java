package com.goodsbridge.shop;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ShopViewController {

    @GetMapping("/test/shop")
    public String index() {
        return "shop/index";
    }
}
