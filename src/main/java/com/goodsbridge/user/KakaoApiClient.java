package com.goodsbridge.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class KakaoApiClient {

    @Value("${oauth2.kakao.client-id}")
    private String kakaoClientId;
    @Value("${oauth2.kakao.redirect-uri}")
    private String redirectUri;
    @Value("${oauth2.kakao.client-secret}")
    private String kakaoClientSecret;

    @Value("${oauth2.kakao.authorization-uri}")
    private String AUTHORIZE_URL;
    @Value("${oauth2.kakao.token-uri}")
    private String TOKEN_URL;
    @Value("${oauth2.kakao.user-info-uri}")
    private String PROFILE_URL;

    private final RestTemplate restTemplate = new RestTemplate();

    public String getAuthorizeUrl() {
        return AUTHORIZE_URL
            + "?client_id=" + kakaoClientId
            + "&redirect_uri=" + redirectUri
            + "&response_type=code";
    }

    // 2. 인가 코드로 액세스 토큰 요청 (POST, 폼 테이터)
    public UserResponse.OauthToken requestAccessToken(String code){

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoClientId);
        params.add("client_secret", kakaoClientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);

        // 바디 + 헤더
        HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(params, headers);

        // 주소, method, HTTP 메세지, 응답 타입
        ResponseEntity<UserResponse.OauthToken> response = restTemplate.exchange(
            TOKEN_URL,
            HttpMethod.POST,
            tokenRequest,
            UserResponse.OauthToken.class
        );
        log.info("카카오 엑세스 토큰 발급 완료"); // 토큰 값은 로그에 남기지 않는다

        return response.getBody();
    }

    // 3. 액세는 토큰으로 사용자 정보 요청 (GET, 인증 헤더)
    public UserResponse.KakaoUserInfo requestProfile(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity request = new HttpEntity(headers);

        ResponseEntity<UserResponse.KakaoUserInfo> response = restTemplate.exchange(
            PROFILE_URL,
            HttpMethod.GET,
            request,
            UserResponse.KakaoUserInfo.class
        );

        return response.getBody();
    }
}
