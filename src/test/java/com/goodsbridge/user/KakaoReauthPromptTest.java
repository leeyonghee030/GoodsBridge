package com.goodsbridge.user;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 탈퇴한 직후 첫 카카오 로그인은 카카오 로그인을 다시 하게 해서, 같은 계정으로 바로 재가입되지 않게 한다
@ExtendWith(MockitoExtension.class)
class KakaoReauthPromptTest {

    @Mock KakaoLoginService kakaoLoginService;
    @Mock UserService userService;
    @InjectMocks UserController userController;

    @Test
    void 탈퇴_직후_카카오_로그인은_다시_로그인하게_하고_표시_쿠키를_지운다() {
        when(kakaoLoginService.getAuthorizeUrl(KakaoApiClient.PROMPT_LOGIN)).thenReturn("https://kakao/auth");
        HttpServletResponse response = mock(HttpServletResponse.class);

        String view = userController.kakaoLogin(false, "1", response);

        assertThat(view).isEqualTo("redirect:https://kakao/auth");
        ArgumentCaptor<String> cookie = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(org.mockito.ArgumentMatchers.eq("Set-Cookie"), cookie.capture());
        assertThat(cookie.getValue()).contains("kakaoReauth=").contains("Max-Age=0");
    }

    @Test
    void 평소_카카오_로그인은_그대로_진행한다() {
        when(kakaoLoginService.getAuthorizeUrl(null)).thenReturn("https://kakao/auth");
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertThat(userController.kakaoLogin(false, null, response)).isEqualTo("redirect:https://kakao/auth");
        verify(response, never()).addHeader(anyString(), anyString());
    }

    @Test
    void 탈퇴하면_다음_카카오_로그인용_표시_쿠키를_남긴다() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        userController.withdraw(1L, response);

        ArgumentCaptor<String> cookie = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(org.mockito.ArgumentMatchers.eq("Set-Cookie"), cookie.capture());
        assertThat(cookie.getValue()).contains("kakaoReauth=1").contains("Path=/");
    }

    @Test
    void 카카오_인가_주소에_prompt와_state가_붙는다() {
        KakaoApiClient client = new KakaoApiClient();
        ReflectionTestUtils.setField(client, "AUTHORIZE_URL", "https://kauth.kakao.com/oauth/authorize");
        ReflectionTestUtils.setField(client, "kakaoClientId", "cid");
        ReflectionTestUtils.setField(client, "redirectUri", "http://localhost:8080/oauth2/kakao");

        assertThat(client.getAuthorizeUrl()).doesNotContain("prompt=").doesNotContain("state=");
        assertThat(client.getAuthorizeUrl(KakaoApiClient.PROMPT_LOGIN))
            .contains("&prompt=login").contains("&state=reselect");
        assertThat(client.getAuthorizeUrl(KakaoApiClient.PROMPT_SELECT_ACCOUNT))
            .contains("&prompt=select_account").contains("&state=reselect");
    }
}
