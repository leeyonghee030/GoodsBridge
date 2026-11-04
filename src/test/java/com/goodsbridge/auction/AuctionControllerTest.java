package com.goodsbridge.auction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuctionControllerTest {

    @Mock
    private AuctionService auctionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuctionViewController(auctionService)).build();
    }

    @Test
    void list_returnsOkWithPagedResponse() throws Exception {
        when(auctionService.findAll(anyInt(), anyInt(), isNull()))
            .thenReturn(new PageImpl<>(java.util.List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/auctions").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    void detail_mapsServiceResponseToOk() throws Exception {
        when(auctionService.findById(eq(999L))).thenReturn(null);

        mockMvc.perform(get("/api/auctions/999").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    void close_mapsServiceResponseToOk() throws Exception {
        when(auctionService.close(1L)).thenReturn(null);

        mockMvc.perform(patch("/api/auctions/1/close"))
            .andExpect(status().isOk());
    }
}
