package com.goodsbridge.board;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class BoardViewController {

    @GetMapping("/test/board")
    public String index() {
        return "board/index";
    }
}
