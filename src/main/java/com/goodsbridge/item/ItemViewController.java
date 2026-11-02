package com.goodsbridge.item;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ItemViewController {

    @GetMapping("/test/item")
    public String index() {
        return "item/index";
    }
}
