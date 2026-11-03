package com.goodsbridge._core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);
    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }
}
