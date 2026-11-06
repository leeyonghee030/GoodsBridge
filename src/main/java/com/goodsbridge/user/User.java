
package com.goodsbridge.user;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

@Getter
@Entity
@Table(name = "user_tb")
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인 및 회원 정보
    @Column(unique = true)
    private String email;

    @Setter
    private String password;

    @Column(unique = true)
    private String nickname;

    private String profileImage;

    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;

    // 일반 유저, 관리자 구분
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'USER'")
    private Role role;

    // 활동 가능 계정인지 비활성화 된 계정인지 구분
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'ACTIVE'")
    private Status status;

    // 가입 경로 구분
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'EMAIL'")   // 문자열이므로 안에 작은 따옴표 필수 !
    private LoginType loginType;

    // JWT Refresh Token 해시값
    @Column(name = "refresh_token_hash", length = 64)
    private String refreshTokenHash;

    @Builder
    public User(String email, String password, String nickname, LoginType loginType, String profileImage) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.loginType = (loginType == null)? LoginType.EMAIL : loginType;
        this.role = Role.USER;
        this.status = Status.ACTIVE;
    }

    public void updateProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public void updatePassword(String password) {
        this.password = password;
    }

    public void updateNickname(String nickname){
        this.nickname = nickname;
    }

    public String getProfilePath() {
        if(this.profileImage == null){
            return null;
        }
        if (this.profileImage.startsWith("http")){
            return this.profileImage;
        }
        return  "/images/" + this.profileImage;

    }

    // Refresh Token 갱신
    public void updateRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }

    // 로그아웃
    public void clearRefreshToken() {
        this.refreshTokenHash = null;
    }

    // 회원 탈퇴
    public void withdraw() {
        this.status = Status.WITHDRAWN;
    }

    // 활동중인 계정인지 확인
    public boolean isActive(){
        return this.status == Status.ACTIVE;
    }
}
