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

    public static final String RESELECT_STATE = "reselect";

    public static final String PROMPT_SELECT_ACCOUNT = "select_account";   // 카카오 계정을 다시 고르게 한다
    public static final String PROMPT_LOGIN = "login";                     // 카카오 로그인을 처음부터 다시 하게 한다

    public String getAuthorizeUrl() {
        return getAuthorizeUrl(null);
    }

    // prompt 가 null 이면 카카오가 로그인돼 있는 계정을 그대로 쓴다.
    // PROMPT_SELECT_ACCOUNT / PROMPT_LOGIN 이면 자동으로 같은 계정을 쓰지 않고 사용자가 다시 고르거나 로그인하게 한다
    public String getAuthorizeUrl(String prompt) {
        return AUTHORIZE_URL
            + "?client_id=" + kakaoClientId
            + "&redirect_uri=" + redirectUri
            + "&response_type=code"
            // state 는 카카오가 콜백에 그대로 돌려준다. 계정 선택/재로그인을 이미 거친 로그인인지 구분하는 데 쓴다
            + (prompt != null ? "&prompt=" + prompt + "&state=" + RESELECT_STATE : "");
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
