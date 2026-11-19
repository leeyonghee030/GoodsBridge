package com.goodsbridge.info;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 푸터(COMPANY)에서 연결되는 안내 화면 4개. 데이터베이스 없이 템플릿만 보여 주는 정적 안내 페이지다.
 * 로그인한 사용자 정보(userId 등)는 CurrentUserAdvice가 모든 화면에 넣어 준다.
 */
@Controller
public class InfoViewController {

    @GetMapping("/about")
    public String about() {
        return "info/about";
    }

    @GetMapping("/shop-guide")
    public String shopGuide() {
        return "info/shop-guide";
    }

    @GetMapping("/notice")
    public String notice() {
        return "info/notice";
    }

    @GetMapping("/faq")
    public String faq() {
        return "info/faq";
    }
}
