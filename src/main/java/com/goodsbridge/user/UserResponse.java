package com.goodsbridge.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

public class UserResponse {
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

    @Data
    public static class UpdateFormDto {
        private String nickname;
        private String email;
        private String profileImage;

        // 휴대폰
        private String phonePrefix;
        private String phoneMiddle;
        private String phoneLast;

        // 주소
        private String postalCode;      // 우편 번호
        private String address;         // 기본 주소
        private String detailAddress;   // 상세 주소 (선택 입력)

        public UpdateFormDto(User user) {
            this.nickname = user.getNickname();
            this.email = user.getEmail();
            this.profileImage = user.getProfilePath();

            // 주소를 저장한 적 없는 사용자는 address 가 null 이다
            if (user.getAddress() != null) {
                this.postalCode = user.getAddress().getPostalCode();
                this.address = user.getAddress().getAddress();
                this.detailAddress = user.getAddress().getDetailAddress();
            }

            String phoneNumber = user.getPhoneNumber();

            if (phoneNumber != null && !phoneNumber.isBlank()) {
                String[] phoneParts = phoneNumber.split("-");

                if (phoneParts.length == 3) {
                    this.phonePrefix = phoneParts[0];
                    this.phoneMiddle = phoneParts[1];
                    this.phoneLast = phoneParts[2];
                }
            }
        }
    }
}
