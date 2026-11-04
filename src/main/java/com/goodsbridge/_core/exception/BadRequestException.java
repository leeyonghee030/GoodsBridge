package com.goodsbridge._core.exception;

public class BadRequestException extends CustomException {
    public BadRequestException(String msg) {
        super(ErrorCode.INVALID_REQUEST, msg);
    }
}
