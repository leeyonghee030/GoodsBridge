package com.goodsbridge.event;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EventViewController {

    @GetMapping("/test/event")
    public String index() {
        return "event/index";
    }
}
