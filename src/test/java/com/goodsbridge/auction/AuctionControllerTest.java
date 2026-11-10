package com.goodsbridge.auction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuctionViewController 슬라이스 테스트 (서비스는 mock).
 * 라우팅 / HTTP 상태코드 / 요청 파라미터·바디가 서비스로 제대로 넘어가는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class AuctionControllerTest {

    @Mock
    private AuctionService auctionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuctionViewController(auctionService)).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    // ---------- 개설 POST /api/shops/{id}/auctions ----------

    @Test
    void create_returnsCreatedAndPassesShopIdUserIdAndBodyToService() throws Exception {
        authenticateAs(1L);
        when(auctionService.create(eq(1L), eq(10L), any(AuctionRequest.class))).thenReturn(null);

        mockMvc.perform(post("/api/shops/10/auctions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"itemId":5,"startPrice":50000,"depositAmount":5000,"deadline":"2030-01-01T00:00:00"}
                    """))
            .andExpect(status().isCreated());

        ArgumentCaptor<AuctionRequest> captor = ArgumentCaptor.forClass(AuctionRequest.class);
        verify(auctionService).create(eq(1L), eq(10L), captor.capture());
        AuctionRequest request = captor.getValue();
        assertEquals(5L, request.getItemId());
        assertEquals(50_000, request.getStartPrice());
        assertEquals(5_000, request.getDepositAmount());
        assertEquals(LocalDateTime.of(2030, 1, 1, 0, 0), request.getDeadline());
    }

    // ---------- 목록 GET /api/auctions ----------

    @Test
    void list_returnsOkWithPagedResponse() throws Exception {
        when(auctionService.findAll(anyInt(), anyInt(), isNull()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/auctions").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        // 파라미터가 없으면 page=1, size=3, status=null(전체)
        verify(auctionService).findAll(1, 3, null);
    }

    @Test
    void list_passesPageSizeAndStatusFilterToService() throws Exception {
        when(auctionService.findAll(2, 5, Status.ACTIVE))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));

        mockMvc.perform(get("/api/auctions")
                .param("page", "2")
                .param("size", "5")
                .param("status", "ACTIVE")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        verify(auctionService).findAll(2, 5, Status.ACTIVE);
    }

    @Test
    void list_rejectsUnknownStatusValueWith400() throws Exception {
        mockMvc.perform(get("/api/auctions").param("status", "NOT_A_STATUS"))
            .andExpect(status().isBadRequest());
    }

    // ---------- 상세 GET /api/auctions/{id} ----------

    @Test
    void detail_mapsServiceResponseToOk() throws Exception {
        when(auctionService.findById(eq(999L))).thenReturn(null);

        mockMvc.perform(get("/api/auctions/999").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        verify(auctionService).findById(999L);
    }

    // ---------- 마감 PATCH /api/auctions/{id}/close ----------

    @Test
    void close_mapsServiceResponseToOk() throws Exception {
        authenticateAs(1L);
        when(auctionService.close(1L, 1L)).thenReturn(null);

        mockMvc.perform(patch("/api/auctions/1/close"))
            .andExpect(status().isOk());

        verify(auctionService).close(1L, 1L);
    }

    // ---------- 잔금 결제 POST /api/auctions/{id}/pay-balance ----------

    @Test
    void payBalance_mapsRequestToServiceAndReturnsOk() throws Exception {
        authenticateAs(1L);
        when(auctionService.payBalance(eq(1L), eq(1L), any(BalancePayRequest.class)))
            .thenReturn(null);

        mockMvc.perform(post("/api/auctions/1/pay-balance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentId\":\"balance-1\"}"))
            .andExpect(status().isOk());

        ArgumentCaptor<BalancePayRequest> captor = ArgumentCaptor.forClass(BalancePayRequest.class);
        verify(auctionService).payBalance(eq(1L), eq(1L), captor.capture());
        assertEquals("balance-1", captor.getValue().getPaymentId());
    }
}
