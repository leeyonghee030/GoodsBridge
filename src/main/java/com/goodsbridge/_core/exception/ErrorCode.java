package com.goodsbridge._core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다"),

    FORBIDDEN_ADMIN_ACCESS(HttpStatus.FORBIDDEN, "AUTH-002, 관리자 권한이 없습니다."),
    SHOP_NOT_FOUND(HttpStatus.NOT_FOUND, "SHOP-001, 존재하지 않는 가게입니다."),
    FORBIDDEN_SHOP_ACCESS(HttpStatus.FORBIDDEN, "SHOP-002, 본인의 가게만 수정 및 삭제할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
