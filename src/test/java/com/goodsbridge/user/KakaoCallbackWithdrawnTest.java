package com.goodsbridge.user;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.security.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KakaoCallbackWithdrawnTest {

    @Mock KakaoLoginService kakaoLoginService;
    @Mock AuthService authService;
    @Mock UserService userService;
    @InjectMocks UserController userController;

    @Test
    void 탈퇴한_계정이_카카오_로그인하면_에러_페이지_대신_로그인_화면으로_안내한다() {
        when(kakaoLoginService.login("code"))
            .thenThrow(new BadRequestException("탈퇴한 계정입니다. 다른 계정으로 로그인해 주세요."));
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = userController.kakaoCallback("code", mock(HttpServletResponse.class), redirectAttributes);

        assertThat(view).isEqualTo("redirect:/login");
        assertThat(redirectAttributes.getFlashAttributes().get("loginError"))
            .isEqualTo("탈퇴한 계정입니다. 다른 계정으로 로그인해 주세요.");
        verify(authService, never()).login(any());
    }
}
