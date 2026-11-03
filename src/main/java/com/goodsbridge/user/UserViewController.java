package com.goodsbridge.user;

import com.goodsbridge._core.security.AuthService;
import com.goodsbridge._core.security.AuthService.TokenResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;

@RequiredArgsConstructor
@Slf4j
@Controller
public class UserViewController {

    private final KakaoLoginService kakaoLoginService;
    private final AuthService authService;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessExpiration;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshExpiration;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @GetMapping("/test/user")
    public String index() {
        return "user/index";
    }

    // 카카오 로그인 페이지 이동
    @GetMapping("/kakao-login")
    public String kakaoLogin() {
        return "redirect:" + kakaoLoginService.getAuthorizeUrl();
    }

    // 카카오 로그인 콜백
    @GetMapping("/oauth/kakao")
    public String kakaoCallback(
        @RequestParam("code") String code,
        HttpServletResponse response
    ) {

        // 1. 카카오 사용자 조회 및 우리 서비스 회원 조회/가입
        User user = kakaoLoginService.login(code);

        // 2. 우리 서비스 JWT 발급
        TokenResponse tokens = authService.login(user);

        // 3. Access Token 쿠키 저장
        ResponseCookie accessCookie = ResponseCookie
            .from("accessToken", tokens.getAccessToken())
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .sameSite("Lax")
            .maxAge(Duration.ofMillis(accessExpiration))
            .build();

        // 4. Refresh Token 쿠키 저장
        ResponseCookie refreshCookie = ResponseCookie
            .from("refreshToken", tokens.getRefreshToken())
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .sameSite("Lax")
            .maxAge(Duration.ofMillis(refreshExpiration))
            .build();

        response.addHeader("Set-Cookie", accessCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());

        // 5. 메인 페이지 이동
        return "redirect:/";
    }
}
