package com.goodsbridge.user;

import com.goodsbridge._core.exception.BadRequestException;

// 탈퇴한 계정으로 카카오 로그인을 시도한 경우. 다른 카카오 계정으로 다시 로그인하도록 안내하는 데 쓴다.
public class WithdrawnUserException extends BadRequestException {
    public WithdrawnUserException() {
        super("탈퇴한 계정입니다. 다른 카카오 계정으로 다시 로그인해 주세요.");
    }
}
