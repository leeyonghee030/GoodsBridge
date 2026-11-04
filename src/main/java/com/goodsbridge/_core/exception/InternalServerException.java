package com.goodsbridge._core.exception;

public class InternalServerException extends CustomException {
    public InternalServerException(String msg) {
        super(ErrorCode.INTERNAL_ERROR, msg);
    }
}
