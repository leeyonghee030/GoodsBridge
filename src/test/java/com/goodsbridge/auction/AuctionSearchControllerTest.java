package com.goodsbridge.auction;

import com.goodsbridge._core.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 경매 검색 API: 숫자 자리에 글자가 오는 등 형식이 틀린 요청은 서버 오류(500)가 아니라 400 으로 돌려준다.
 */
class AuctionSearchControllerTest {
    private final AuctionSearchService service = mock(AuctionSearchService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuctionSearchController(service))
        .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void malformedPageNumberIsBadRequestNotServerError() throws Exception {
        mockMvc.perform(get("/api/auctions/search").param("tab", "ACTIVE").param("page", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void validRequestReturnsOk() throws Exception {
        when(service.searchPublic(isNull(), anyString(), any(), anyString(), any(), anyInt(), anyInt()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 12), 0));

        mockMvc.perform(get("/api/auctions/search").param("tab", "ACTIVE").param("page", "0"))
            .andExpect(status().isOk());
    }
}
