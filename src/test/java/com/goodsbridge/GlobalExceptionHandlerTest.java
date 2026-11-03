package com.goodsbridge;

import com.goodsbridge._core.exception.CustomException;
import com.goodsbridge._core.exception.ErrorCode;
import com.goodsbridge._core.exception.ErrorResponse;
import com.goodsbridge._core.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    // 테스트용 구체 클래스
    private static class TestCustomException extends CustomException {
        public TestCustomException(ErrorCode errorCode) {
            super(errorCode);
        }
    }

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("CustomException 발생 시 지정된 에러 코드와 HTTP 상태를 반환한다")
    void handleCustomException() {

        // given
        CustomException exception =
            new TestCustomException(ErrorCode.NOT_FOUND);

        // when
        ResponseEntity<ErrorResponse> response =
            handler.handleCustomException(exception);

        // then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.NOT_FOUND.name(), response.getBody().getCode());
        assertEquals(ErrorCode.NOT_FOUND.getMessage(), response.getBody().getMessage());
    }

    @Test
    @DisplayName("예상하지 못한 예외 발생 시 INTERNAL_ERROR를 반환한다")
    void handleUnexpectedException() {

        // given
        Exception exception = new RuntimeException("예상하지 못한 오류");

        // when
        ResponseEntity<ErrorResponse> response =
            handler.handleUnexpectedException(exception);

        // then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INTERNAL_ERROR.name(), response.getBody().getCode());
        assertEquals(ErrorCode.INTERNAL_ERROR.getMessage(), response.getBody().getMessage());
    }
}
