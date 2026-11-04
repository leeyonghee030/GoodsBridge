package com.goodsbridge._core.exception;

public class NotFoundException extends CustomException {
    public NotFoundException(String msg) {
        super(ErrorCode.NOT_FOUND, msg);
    }
}
