package com.goodsbridge._core.exception;

public class InternalServerException extends CustomException {
    public InternalServerException(ErrorCode errorCode) {
        super(errorCode);
    }
}
