package com.goodsbridge._core.security;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import com.goodsbridge._core.exception.CustomException;
import com.goodsbridge._core.exception.ErrorCode;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 토큰 재발급
    @PostMapping("/reissue")
    public void reissue(
        @CookieValue(name = "refreshToken", required = false) String refreshToken,
        HttpServletResponse response
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new CustomException(ErrorCode.UNAUTHORIZED, "Refresh Token 쿠키가 없습니다.");
        }
        AuthService.TokenResponse tokens = authService.reissue(refreshToken);
        authService.addAuthCookies(response, tokens);
    }

    // 로그아웃
    @PostMapping("/logout")
    public void logout(@AuthenticationPrincipal Long userId, HttpServletResponse response) {
        authService.logout(userId);
        authService.clearAuthCookies(response);
    }
}
