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
    void 탈퇴한_계정이_카카오_로그인하면_안내하고_계정_선택을_다시_하도록_표시한다() {
        when(kakaoLoginService.login("code")).thenThrow(new WithdrawnUserException());
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = userController.kakaoCallback("code", null, mock(HttpServletResponse.class), redirectAttributes);

        assertThat(view).isEqualTo("redirect:/login");
        assertThat(redirectAttributes.getFlashAttributes().get("loginError")).isNotNull();
        assertThat(redirectAttributes.getFlashAttributes().get("kakaoRetry")).isEqualTo(true);
        verify(authService, never()).login(any());
    }

    @Test
    void 계정_선택을_거친_뒤에도_탈퇴한_계정이면_다시_카카오로_보내지_않고_안내만_한다() {
        when(kakaoLoginService.login("code")).thenThrow(new WithdrawnUserException());
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = userController.kakaoCallback("code", KakaoApiClient.RESELECT_STATE,
            mock(HttpServletResponse.class), redirectAttributes);

        assertThat(view).isEqualTo("redirect:/login");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("loginError");
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey("kakaoRetry");
    }

    @Test
    void 탈퇴가_아닌_다른_로그인_실패는_안내만_하고_카카오를_다시_띄우지_않는다() {
        when(kakaoLoginService.login("code")).thenThrow(new BadRequestException("카카오 인가 코드가 전달되지 않았습니다."));
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = userController.kakaoCallback("code", null, mock(HttpServletResponse.class), redirectAttributes);

        assertThat(view).isEqualTo("redirect:/login");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("loginError");
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey("kakaoRetry");
    }

    @Test
    void 이메일_로그인이_거절되면_에러_페이지_대신_로그인_화면에서_안내한다() {
        when(userService.login(any())).thenThrow(new BadRequestException("사용자명 또는 비밀번호가 올바르지 않습니다"));
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = userController.login(mock(UserRequest.LoginDto.class), mock(HttpServletResponse.class), redirectAttributes);

        assertThat(view).isEqualTo("redirect:/login");
        assertThat(redirectAttributes.getFlashAttributes().get("loginError"))
            .isEqualTo("사용자명 또는 비밀번호가 올바르지 않습니다");
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey("kakaoRetry");
    }
}
