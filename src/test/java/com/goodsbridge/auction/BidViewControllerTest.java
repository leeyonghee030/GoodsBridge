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
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이슈 #20 입찰 API 슬라이스 테스트 (서비스는 mock).
 * (기존 test/.../BidController.java는 테스트 메서드가 하나도 없는 빈 @SpringBootTest 클래스라 이 파일로 대체)
 */
@ExtendWith(MockitoExtension.class)
class BidViewControllerTest {

    @Mock
    private BidService bidService;

    @Mock
    private AuctionService auctionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new BidViewController(bidService), new AuctionViewController(auctionService))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    // ---------- 입찰 POST /api/auctions/{id}/bids ----------

    @Test
    void placeBid_returnsCreatedAndPassesAuctionIdUserIdAndBodyToService() throws Exception {
        authenticateAs(2L);
        when(bidService.placeBid(eq(1L), eq(2L), any(BidRequest.class))).thenReturn(null);

        mockMvc.perform(post("/api/auctions/1/bids")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bidAmount\":12000,\"paymentId\":\"payment-1\"}"))
            .andExpect(status().isCreated());

        ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(bidService).placeBid(eq(1L), eq(2L), captor.capture());
        assertEquals(12_000, captor.getValue().getBidAmount());
        assertEquals("payment-1", captor.getValue().getPaymentId());
    }

    // ---------- 입찰 내역 GET /api/auctions/{id}/bids ----------

    @Test
    void bidsList_returnsOkAndPassesPageableToService() throws Exception {
        when(bidService.getBids(eq(1L), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));

        mockMvc.perform(get("/api/auctions/1/bids")
                .param("page", "0")
                .param("size", "5")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bidService).getBids(eq(1L), captor.capture());
        assertEquals(5, captor.getValue().getPageSize());
    }

    // ---------- 내가 참여한 경매 GET /api/auctions/me ----------

    @Test
    void myBid_usesCurrentUserId() throws Exception {
        authenticateAs(2L);
        when(bidService.participateAuction(eq(2L), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/auctions/me").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        verify(bidService).participateAuction(eq(2L), any(Pageable.class));
    }

    @Test
    void myBid_isNotCapturedByAuctionDetailPathVariable() throws Exception {
        // /api/auctions/me 가 AuctionViewController의 /api/auctions/{id} 로 잘못 매핑되면
        // "me"를 Long으로 변환하지 못해 400이 난다. 리터럴 경로가 우선하는지 확인.
        authenticateAs(2L);
        when(bidService.participateAuction(eq(2L), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/auctions/me"))
            .andExpect(status().isOk());

        verify(auctionService, never()).findById(anyLong());
    }
}
