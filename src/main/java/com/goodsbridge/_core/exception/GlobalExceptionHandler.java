package com.goodsbridge._core.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {

        ErrorCode errorCode = e.getErrorCode();

        return ResponseEntity
            .status(errorCode.getStatus())
            .body(ErrorResponse.of(errorCode, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException e) {

        String message = e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse("요청 값이 올바르지 않습니다.");

        BadRequestException exception = new BadRequestException(message);

        return ResponseEntity
            .status(exception.getErrorCode().getStatus())
            .body(ErrorResponse.of(
                exception.getErrorCode(),
                exception.getMessage()
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {

        log.error("예상하지 못한 서버 오류 발생", e);

        return ResponseEntity
            .status(ErrorCode.INTERNAL_ERROR.getStatus())
            .body(ErrorResponse.of(
                ErrorCode.INTERNAL_ERROR,
                "서버 내부 오류가 발생했습니다."
            ));
    }
}
