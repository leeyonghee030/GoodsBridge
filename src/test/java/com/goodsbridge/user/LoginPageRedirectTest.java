package com.goodsbridge.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

// 로그인은 헤더의 팝업으로 통일: /login 은 홈으로 보내면서 팝업을 열도록 표시한다
@ExtendWith(MockitoExtension.class)
class LoginPageRedirectTest {

    @InjectMocks UserController userController;

    @Test
    void 로그아웃_상태로_login에_들어오면_홈으로_보내며_팝업을_열도록_표시한다() {
        assertThat(userController.loginForm(null)).isEqualTo("redirect:/?login=open");
    }

    @Test
    void 이미_로그인한_사용자는_그냥_홈으로_보낸다() {
        assertThat(userController.loginForm(1L)).isEqualTo("redirect:/");
    }
}
