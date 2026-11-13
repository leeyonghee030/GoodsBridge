package com.goodsbridge._core.exception;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(
        CustomException e, HandlerMethod handlerMethod, HttpServletResponse response) throws IOException {

        ErrorCode errorCode = e.getErrorCode();

        // 화면 컨트롤러(@ResponseBody 없음)에서 난 예외는 JSON 대신 templates/error/ 오류 페이지로 보낸다
        if (isViewHandler(handlerMethod)) {
            response.sendError(errorCode.getStatus().value(), e.getMessage());
            return null;
        }

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

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailureException(
        ObjectOptimisticLockingFailureException e) {

        log.warn("[OptimisticLock] 동시 수정 충돌 발생: {}", e.getMessage());

        ErrorCode errorCode = ErrorCode.CONFLICT;

        return ResponseEntity
            .status(errorCode.getStatus())
            .body(ErrorResponse.of(errorCode, "다시 시도해주세요."));
    }

    // 없는 주소는 Exception 핸들러(500)로 가지 않도록 404로 넘긴다. 화면이면 error/404, API면 Boot 기본 JSON
    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResourceFoundException(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    // @Controller 안에서도 ResponseEntity를 돌려주는 메서드는 API로 본다 (예: ItemViewController의 /api/items)
    private boolean isViewHandler(HandlerMethod handlerMethod) {
        return handlerMethod != null
            && !HttpEntity.class.isAssignableFrom(handlerMethod.getReturnType().getParameterType())
            && !handlerMethod.hasMethodAnnotation(ResponseBody.class)
            && !AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), ResponseBody.class);
    }
}
