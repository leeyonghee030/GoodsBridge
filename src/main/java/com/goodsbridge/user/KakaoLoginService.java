package com.goodsbridge.user;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ServiceUnavailableException;
import com.goodsbridge._core.exception.UnauthorizedException;
import com.goodsbridge._core.security.AuthService;
import com.goodsbridge._core.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoLoginService {

    private final KakaoApiClient kakaoApiClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public String getAuthorizeUrl() {
        return kakaoApiClient.getAuthorizeUrl();
    }

    public AuthService.TokenResponse login(String code) {

        if (code == null || code.isBlank()) {
            throw new BadRequestException(
                "카카오 인가 코드가 전달되지 않았습니다."
            );
        }

        try {
            // 1. 인가 코드로 카카오 Access Token 요청
            UserResponse.OauthToken oAuthToken =
                kakaoApiClient.requestAccessToken(code);

            // 2. Access Token으로 사용자 정보 요청
            UserResponse.KakaoUserInfo profile =
                kakaoApiClient.requestProfile(oAuthToken.getAccessToken());

            // 3. 카카오 사용자 정보 추출
            // 기존 DTO에 맞춰 kakaoId, nickname 추출
            String kakaoId = String.valueOf(profile.getId());
            String nickname = profile.getProperties().getNickname();


            // 4. 기존 회원 조회 또는 신규 회원 가입
            User user = userRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> userRepository.save(
                    new User(kakaoId, null, nickname, Role.USER)
                ));

            // 5. JWT 발급
            String accessToken = jwtProvider.createAccessToken(
                user.getId(),
                user.getRole().name()
            );

            String refreshToken = jwtProvider.createRefreshToken(
                user.getId()
            );

            // 6. 로그인 응답 생성
            return AuthService.TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(user)
                .build();

        } catch (HttpClientErrorException e) {
            // 카카오 API 4xx 오류
            log.warn("카카오 인증 요청 실패: status={}", e.getStatusCode());
            throw new UnauthorizedException(
                "카카오 인증에 실패했습니다. 다시 로그인해 주세요."
            );
        } catch (HttpServerErrorException e) {
            // 카카오 서버 5xx 오류
            log.error("카카오 서버 오류: status={}", e.getStatusCode());
            throw new ServiceUnavailableException("카카오 인증 서버에 일시적인 문제가 발생했습니다.");
        } catch (ResourceAccessException e) {
            // 네트워크 오류 및 연결 시간 초과
            log.error("카카오 서버 연결 실패", e);
            throw new ServiceUnavailableException("카카오 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
