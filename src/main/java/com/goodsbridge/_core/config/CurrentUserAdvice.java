package com.goodsbridge._core.config;

import com.goodsbridge._core.util.SecurityUtil;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 모든 Mustache 화면의 헤더가 로그인 상태를 알 수 있게 userId 를 기본으로 내려준다.
 * (컨트롤러가 model 에 userId 를 따로 넣으면 그 값이 우선한다)
 * 로그인 화면처럼 userId 를 안 넣는 페이지에서 로그인했는데도 헤더에 LOGIN 이 보이던 문제를 막는다.
 */
@ControllerAdvice
public class CurrentUserAdvice {

    @ModelAttribute("userId")
    public Long userId() {
        return SecurityUtil.getCurrentUserIdOrNull();
    }
}
