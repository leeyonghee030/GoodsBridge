package com.goodsbridge._core.exception;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ErrorResponse {

    private final String code;
    private final String message;
    private final LocalDateTime timestamp;

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return ErrorResponse.builder()
                .code(errorCode.name())
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
