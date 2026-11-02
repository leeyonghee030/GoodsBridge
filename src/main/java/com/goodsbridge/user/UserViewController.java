package com.goodsbridge.user;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserViewController {

    @GetMapping("/test/user")
    public String index() {
        return "user/index";
    }
}
