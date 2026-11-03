package com.goodsbridge.user;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ServiceUnavailableException;
import com.goodsbridge._core.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KakaoLoginServiceTest {

    private KakaoApiClient kakaoApiClient;
    private UserRepository userRepository;
    private KakaoLoginService kakaoLoginService;

    @BeforeEach
    void setUp() {
        kakaoApiClient = mock(KakaoApiClient.class);
        userRepository = mock(UserRepository.class);
        kakaoLoginService = new KakaoLoginService(kakaoApiClient, userRepository);
    }

    @Test
    void 새_카카오_사용자는_회원가입하고_반환한다() {
        // given
        String code = "authorization-code";
        UserResponse.OauthToken token = oauthToken("kakao-access-token");
        UserResponse.KakaoUserInfo profile = kakaoProfile(12345L, "새회원");

        when(kakaoApiClient.requestAccessToken(code)).thenReturn(token);
        when(kakaoApiClient.requestProfile("kakao-access-token")).thenReturn(profile);
        when(userRepository.findByKakaoId("12345")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        User result = kakaoLoginService.login(code);

        // then
        assertEquals("12345", result.getKakaoId());
        assertEquals("새회원", result.getNickname());
        assertEquals(Role.USER, result.getRole());

        verify(kakaoApiClient).requestAccessToken(code);
        verify(kakaoApiClient).requestProfile("kakao-access-token");
        verify(userRepository).findByKakaoId("12345");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void 기존_카카오_사용자는_새로_저장하지_않고_반환한다() {
        // given
        String code = "authorization-code";
        User existingUser = new User("12345", null, "기존회원", Role.USER);

        when(kakaoApiClient.requestAccessToken(code))
            .thenReturn(oauthToken("kakao-access-token"));
        when(kakaoApiClient.requestProfile("kakao-access-token"))
            .thenReturn(kakaoProfile(12345L, "카카오닉네임"));
        when(userRepository.findByKakaoId("12345"))
            .thenReturn(Optional.of(existingUser));

        // when
        User result = kakaoLoginService.login(code);

        // then
        assertSame(existingUser, result);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void 인가_코드가_null이면_BadRequestException을_던진다() {
        assertThrows(
            BadRequestException.class,
            () -> kakaoLoginService.login(null)
        );

        verifyNoInteractions(kakaoApiClient, userRepository);
    }

    @Test
    void 인가_코드가_빈_문자열이면_BadRequestException을_던진다() {
        assertThrows(
            BadRequestException.class,
            () -> kakaoLoginService.login("   ")
        );

        verifyNoInteractions(kakaoApiClient, userRepository);
    }

    @Test
    void 카카오가_4xx를_반환하면_UnauthorizedException으로_변환한다() {
        // given
        when(kakaoApiClient.requestAccessToken("bad-code"))
            .thenThrow(HttpClientErrorException.create(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                org.springframework.http.HttpHeaders.EMPTY,
                new byte[0],
                null
            ));

        // when / then
        assertThrows(
            UnauthorizedException.class,
            () -> kakaoLoginService.login("bad-code")
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void 카카오가_5xx를_반환하면_ServiceUnavailableException으로_변환한다() {
        // given
        when(kakaoApiClient.requestAccessToken("valid-code"))
            .thenThrow(HttpServerErrorException.create(
                org.springframework.http.HttpStatus.BAD_GATEWAY,
                "Bad Gateway",
                org.springframework.http.HttpHeaders.EMPTY,
                new byte[0],
                null
            ));

        // when / then
        assertThrows(
            ServiceUnavailableException.class,
            () -> kakaoLoginService.login("valid-code")
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void 카카오_API_연결에_실패하면_ServiceUnavailableException으로_변환한다() {
        // given
        when(kakaoApiClient.requestAccessToken("valid-code"))
            .thenThrow(new ResourceAccessException("connection timed out"));

        // when / then
        assertThrows(
            ServiceUnavailableException.class,
            () -> kakaoLoginService.login("valid-code")
        );

        verifyNoInteractions(userRepository);
    }

    private UserResponse.OauthToken oauthToken(String accessToken) {
        UserResponse.OauthToken token = new UserResponse.OauthToken();
        token.setAccessToken(accessToken);
        return token;
    }

    private UserResponse.KakaoUserInfo kakaoProfile(Long id, String nickname) {
        UserResponse.KakaoUserInfo.Properties properties =
            new UserResponse.KakaoUserInfo.Properties();
        properties.setNickname(nickname);

        UserResponse.KakaoUserInfo profile =
            new UserResponse.KakaoUserInfo();
        profile.setId(id);
        profile.setProperties(properties);
        return profile;
    }
}
