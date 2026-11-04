
package com.goodsbridge.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "user_tb")
@NoArgsConstructor
@AllArgsConstructor
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String profileImage;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "user_id", nullable = false)
    private List<UserRole> roles = new ArrayList<>();

    // JWT Refresh Token 해시값
    @Column(name = "refresh_token_hash", length = 64)
    private String refreshTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'LOCAL'")   // 문자열이므로 안에 작은 따옴표 필수 !
    private OAuthProvider provider;

    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'ACTIVE'")
    private Status status;

    @Builder
    public User(String nickname, String password, String email, String profileImage, OAuthProvider provider) {
        this.nickname = nickname;
        this.email = email;
        this.password = password;
        this.profileImage = profileImage;
        this.provider = (provider == null)? OAuthProvider.LOCAL : provider;
    }

    public boolean hasRole(Role role){
        for (UserRole u : this.roles){
            if (u.getRole() == role){
                return true;    // 찾으면 바로 true
            }
        }
        return false;   // 끝까지 없으면 false
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN); // 고정 값 : 관리자 여부인지만 확인 true, false 반환
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
