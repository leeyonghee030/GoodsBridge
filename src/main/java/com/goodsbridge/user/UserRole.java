package com.goodsbridge.user;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "user_role_tb",
    uniqueConstraints = {@UniqueConstraint(name = "uk_user_role", columnNames = {"user_id", "role"})})

public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // enum 을 숫자가 아닌 문자열(ADMIN, USER)로 저장
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Builder    // id는 자동으로 채워지므로 빌더에서 제외
    public UserRole(Role role){
        this.role = role;
    }
}
