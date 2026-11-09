package com.goodsbridge.board;

import com.goodsbridge._core.util.SecurityUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class BoardViewController {

    @GetMapping("/test/board")
    public String index(Model model) {
        model.addAttribute("userId", SecurityUtil.getCurrentUserIdOrNull());
        return "board/index";
    }
}
