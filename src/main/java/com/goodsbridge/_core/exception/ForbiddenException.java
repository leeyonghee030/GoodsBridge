package com.goodsbridge._core.exception;

public class ForbiddenException extends CustomException {
    public ForbiddenException(String msg) {
        super(ErrorCode.FORBIDDEN, msg);
    }
}
