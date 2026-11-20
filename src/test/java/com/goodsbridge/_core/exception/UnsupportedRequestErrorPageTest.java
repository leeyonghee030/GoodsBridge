package com.goodsbridge._core.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 잘못된 방식(405)·형식(415)의 요청은 본문 없이 응답하지 않고 sendError 로 넘긴다.
 * 그래야 브라우저로 열었을 때 templates/error 오류 페이지가 뜨고, API 클라이언트는 Boot 기본 JSON 을 받는다.
 */
class UnsupportedRequestErrorPageTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void methodNotAllowed_goesToTheErrorPage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handleUnsupportedRequest(new HttpRequestMethodNotSupportedException("GET"), response);

        assertEquals(405, response.getStatus());
        assertTrue(response.isCommitted(), "sendError 로 넘겨야 오류 페이지가 그려진다 (본문 없는 응답이면 브라우저 기본 화면이 뜬다)");
    }

    @Test
    void unsupportedMediaType_goesToTheErrorPage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handleUnsupportedRequest(new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, java.util.List.of(MediaType.APPLICATION_JSON)), response);

        assertEquals(415, response.getStatus());
        assertTrue(response.isCommitted());
    }
}
