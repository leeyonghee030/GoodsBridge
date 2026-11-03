package com.goodsbridge._core.exception;

public class ForbiddenException extends CustomException {
    public ForbiddenException(ErrorCode errorCode, String msg) {
        super(errorCode, msg);
    }
}
