package com.goodsbridge._core.exception;

public class ServiceUnavailableException extends CustomException {
    public ServiceUnavailableException(String msg) {
        super(ErrorCode.SERVICE_UNAVAILABLE, msg);
    }
}
