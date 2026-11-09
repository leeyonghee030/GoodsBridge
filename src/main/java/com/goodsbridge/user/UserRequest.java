package com.goodsbridge.user;

import com.goodsbridge._core.exception.BadRequestException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

public class UserRequest {

    @Data
    public static class UpdateDto {
        private String password;
        private MultipartFile profileImage;
        private String nickname;

        public boolean hasPassword() {
            return password != null && !password.isBlank();
        }

        public boolean hasProfileImage() {
            return profileImage != null && !profileImage.isEmpty();
        }

        public boolean hasNickname(){
            return nickname != null && !nickname.isBlank();
        }

        public void validate(){
            if (!hasNickname() && !hasPassword() && !hasProfileImage()){
                throw new BadRequestException("변경할 데이터를 입력해주세요");
            }

            if (hasPassword() && password.length() < 8){
                throw new BadRequestException("패스워드는 8자 이상이어야 합니다");
            }
        }

    }

    @Data
    public static class JoinDto {
        @NotBlank
        @Email
        private String email;
        @NotBlank
        @Size(min = 8, max = 20)
        private String password;
        @NotBlank
        private String nickname;

        // 이메일 인증 확인 토큰
//        @NotBlank
        private String verificationToken;

        // DTO 에서 User 엔티티로 변환하는 메서드
        // 계층 간 데이터 변환을 명확하게 분리하는 것이 좋다.
        // toEntity() <-- 메서드 이름 (이 메서드를 호출하면 User 객체를 반환하는 코드를 작성하시오)
        public User toEntity(String encodePassword) {
            return User.builder()
                .email(email)
                .password(encodePassword)
                .nickname(nickname)
                .build();
        }
    }

    @Data
    public static class LoginDto {
        @Email
        @NotBlank
        private String email;
        @NotBlank
        private String password;
    }
}
