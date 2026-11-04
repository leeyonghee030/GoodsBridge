package com.goodsbridge.user;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.security.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class UserService {

    private UserRepository userRepository;

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
        String email = kakaoUserInfo.getId() + "kakao.com";

        // 2. 이미 가입 했는지 확인, 없으면 자동 회원가입
        User user = userRepository.findByUsernameWithRoles(email)
            .orElseGet(() -> joinKakaoUser(email, kakaoUserInfo));

        // 3. 같은 이름의 일반 회원이 있다면 카카오 사용자로 로그인 시키지 않음
        if (user.getProvider() != OAuthProvider.KAKAO) {
            throw new BadRequestException("이미 사용 중인 사용자명입니다");
        }

        // 트랜잭션 안에서 SessionUserDto 생성
        return new SessionUserDto(user);
    }

    private User joinKakaoUser(String email, UserResponse.KakaoUserInfo kakaoUserInfo) {
//        User newUser = User.builder()
//            .email(email)
//            // 카카오 사용자는 비밀번호로 로그인하지 않으므로 아무도 알 수 없는 값을 넣음
//            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
//            .profileImage(kakaoUserInfo.getProperties().getProfileImage())
//            .provider(OAuthProvider.KAKAO)
//            .build();
//        newUser.addRole(Role.USER);
//        return userJpaRepository.save(newUser);
//        TODO - 카카오 로그인 미완
        return null;
    }

}
