package com.goodsbridge.user;

import lombok.Data;

@Data
public class SessionUserDto {
    private Long id;
    private String username;
    private String  email;
    private boolean admin;      // 관리자 여부 lombok (isAdmin) - boolean
    private String roleDisplay; // 화면 표시용 (ADMIN 또는 USER)

    public SessionUserDto(User user) {
        this.id = user.getId();
        this.username = user.getNickname();
        this.email = user.getEmail();
        this.admin = user.isAdmin();    // 관리자면 true
        this.roleDisplay = this.admin ? "ADMIN" : "USER";
    }
}
