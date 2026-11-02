package com.goodsbridge.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminViewController {

    @GetMapping("/test/admin")
    public String index() {
        return "admin/index";
    }
}
