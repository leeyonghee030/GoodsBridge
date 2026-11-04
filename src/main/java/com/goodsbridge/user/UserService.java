package com.goodsbridge.user;

import com.goodsbridge._core.security.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class UserService {

    @Value("${jwt.access-token-expiration-ms}")
    private long accessExpiration;
    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshExpiration;
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    public ResponseCookie getaccessCookie(AuthService.TokenResponse tokens) {
        return ResponseCookie
            .from("accessToken", tokens.getAccessToken())
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .sameSite("Lax")
            .maxAge(Duration.ofMillis(accessExpiration))
            .build();
    }


    public ResponseCookie getRefreshToken(AuthService.TokenResponse tokens) {
        return ResponseCookie
            .from("refreshToken", tokens.getRefreshToken())
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .sameSite("Lax")
            .maxAge(Duration.ofMillis(refreshExpiration))
            .build();
    }

    // 카카오 로그인 : 처음이면 자동 회원가입, 이미 있으면 로그인
    @Transactional
    public SessionUserDto kakaoLogin(UserResponse.KakaoUserInfo kakaoUserInfo) {

        // 1. 카카오 회원번호로 우리 서비스의 사용자명 만들기
        String username = "kakao_" + kakaoUserInfo.getId();

        // 2. 이미 가입 했는지 확인, 없으면 자동 회원가입
        User user = userJpaRepository.findByUsernameWithRoles(username)
            .orElseGet(() -> joinKakaoUser(username, kakaoUserInfo));

        // 3. 같은 이름의 일반 회원이 있다면 카카오 사용자로 로그인 시키지 않음
        if (user.getProvider() != OAuthProvider.KAKAO) {
            throw new Exception400("이미 사용 중인 사용자명입니다");
        }

        // 트랜잭션 안에서 SessionUserDto 생성
        return new SessionUserDto(user);
    }

}
