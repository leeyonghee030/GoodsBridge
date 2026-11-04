package com.goodsbridge._core.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 토큰 재발급
    @PostMapping("/reissue")
    public AuthService.TokenResponse reissue(
        @Valid @RequestBody RefreshTokenRequest request
    ) {
        return authService.reissue(request.getRefreshToken());
    }

    // 로그아웃
    @PostMapping("/logout")
    public void logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
    }

    @Getter
    @NoArgsConstructor
    public static class RefreshTokenRequest {

        @NotBlank
        private String refreshToken;
    }
}
