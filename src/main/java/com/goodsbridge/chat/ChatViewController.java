package com.goodsbridge.chat;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatViewController {

    @GetMapping("/test/chat")
    public String index() {
        return "chat/index";
    }
}
