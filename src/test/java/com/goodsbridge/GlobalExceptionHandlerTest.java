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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    // 테스트용 구체 클래스
    private static class TestCustomException extends CustomException {
        public TestCustomException(ErrorCode errorCode, String message) {
            super(errorCode, message);
        }
    }

    @Controller
    static class TestViewController {
        public String page() { return "page"; }
        public ResponseEntity<String> api() { return ResponseEntity.ok("api"); }
    }

    @RestController
    static class TestApiController {
        public String api() { return "api"; }
    }

    private static HandlerMethod handlerOf(Object controller, String methodName) throws Exception {
        return new HandlerMethod(controller, controller.getClass().getMethod(methodName));
    }

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("CustomException 발생 시 지정된 에러 코드와 HTTP 상태를 반환한다")
    void handleCustomException() throws Exception {

        // given
        CustomException exception =
            new TestCustomException(ErrorCode.NOT_FOUND, "테스트 메시지");

        // when
        ResponseEntity<ErrorResponse> response =
            handler.handleCustomException(exception, handlerOf(new TestApiController(), "api"), new MockHttpServletResponse());

        // then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.NOT_FOUND.name(), response.getBody().getCode());
        assertEquals("테스트 메시지", response.getBody().getMessage());
    }

    @Test
    @DisplayName("화면 컨트롤러에서 CustomException 발생 시 JSON 대신 해당 상태코드로 오류 페이지를 띄운다")
    void handleCustomException_view() throws Exception {

        // given
        CustomException exception =
            new TestCustomException(ErrorCode.NOT_FOUND, "테스트 메시지");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        // when
        ResponseEntity<ErrorResponse> response =
            handler.handleCustomException(exception, handlerOf(new TestViewController(), "page"), servletResponse);

        // then
        assertNull(response);
        assertEquals(404, servletResponse.getStatus());
        assertEquals("테스트 메시지", servletResponse.getErrorMessage());
    }

    @Test
    @DisplayName("@Controller 안이라도 ResponseEntity를 돌려주는 메서드면 JSON으로 응답한다")
    void handleCustomException_responseEntityInViewController() throws Exception {

        // given
        CustomException exception =
            new TestCustomException(ErrorCode.NOT_FOUND, "테스트 메시지");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        // when
        ResponseEntity<ErrorResponse> response =
            handler.handleCustomException(exception, handlerOf(new TestViewController(), "api"), servletResponse);

        // then
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(servletResponse.getErrorMessage());
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
        assertEquals("서버 내부 오류가 발생했습니다.", response.getBody().getMessage());
    }
}
