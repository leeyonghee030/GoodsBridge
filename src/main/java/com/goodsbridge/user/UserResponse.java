package com.goodsbridge.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

public class UserResponse {
    // 카카오 액세스 토큰 응답 DTO

    @Data
    public static class JoinDto {
        private Long id;
        private String email;

        public JoinDto(User user) {
            this.id = user.getId();
            this.email = user.getEmail();
        }
    }

    @Data
    public static class OauthToken {
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("token_type")
        private String tokenType;
        @JsonProperty("refresh_token")
        private String refreshToken;
        @JsonProperty("expires_in")
        private Long expiresIn;
        @JsonProperty("scope")
        private String scope;
        @JsonProperty("refresh_token_expires_in")
        private Long refreshTokenExpiresIn;
    }

    @Data
    public static class KakaoUserInfo {

        private Long id;
        @JsonProperty("connected_at")
        private String connectedAt;
        private Properties properties;
        @JsonProperty("kakao_account")
        private Object kakaoAccount;

        @Data
        public static class Properties {

            private String nickname;
            @JsonProperty("profile_image")
            private String profileImage;
            @JsonProperty("thumbnail_image")
            private String thumbnailImage;
        }
    }

}
