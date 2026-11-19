
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

    @Embedded
    private Address address; // 배송 주소

    private String phoneNumber;

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

    // 주소·전화번호 없이 만드는 기존 호출부(테스트 등)를 위해 유지한다
    public User(String email, String password, String nickname, LoginType loginType, String profileImage) {
        this(email, password, nickname, loginType, profileImage, null, null);
    }

    @Builder
    public User(String email, String password, String nickname, LoginType loginType, String profileImage, Address address, String phoneNumber) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.address = address;
        this.phoneNumber = phoneNumber;
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

    public void updatePhoneNumber(String phoneNumber){this.phoneNumber = phoneNumber;}

    public void updateAddress(Address address){this.address = address;}

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
    // 계정 행은 과거 구매·교환·정산 기록 때문에 남겨 두되, 같은 이메일·카카오 계정·닉네임으로 다시 가입할 수 있도록
    // unique 컬럼(이메일, 닉네임)을 익명 값으로 바꾸고 프로필 사진을 지운다. 비밀번호로는 이메일이 없어 로그인할 수 없다.
    public void withdraw() {
        this.status = Status.WITHDRAWN;
        this.email = "withdrawn-" + this.id + "@withdrawn.invalid";
        this.nickname = "탈퇴회원" + this.id;
        this.profileImage = null;
    }

    // 활동중인 계정인지 확인
    public boolean isActive(){
        return this.status == Status.ACTIVE;
    }
}
