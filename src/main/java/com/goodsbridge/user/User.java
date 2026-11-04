
package com.goodsbridge.user;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

    // 카카오 로그인 식별자
    @Column(unique = true)
    private String kakaoId;

    // 로그인 및 회원 정보
    @Column(unique = true)
    private String email;

    // 추후 일반 로그인 구현용
    private String password;

    @Column(unique = true)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // JWT Refresh Token 해시값
    @Column(name = "refresh_token_hash", length = 64)
    private String refreshTokenHash;

    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    // 카카오 회원가입 생성자
    @Builder
    public User(String kakaoId, String email,
                String nickname, Role role) {
        this.kakaoId = kakaoId;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
    }

    // Refresh Token 갱신
    public void updateRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }

    // 로그아웃
    public void clearRefreshToken() {
        this.refreshTokenHash = null;
    }
}
