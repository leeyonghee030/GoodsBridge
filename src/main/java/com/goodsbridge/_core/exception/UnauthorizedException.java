package com.goodsbridge._core.exception;

public class UnauthorizedException extends CustomException {
    public UnauthorizedException(String msg) {
        super(ErrorCode.UNAUTHORIZED, msg);
    }
}
