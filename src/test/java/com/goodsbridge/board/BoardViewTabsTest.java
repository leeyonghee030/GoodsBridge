package com.goodsbridge.board;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 게시판 화면: 유형 탭과, 유형별 글쓰기 폼(상품 필수 / 비밀글 선택) */
@SpringBootTest
@ActiveProfiles("test")
class BoardViewTabsTest {

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void showsAllFourTabs_andMarksTheCurrentOne() throws Exception {
        mockMvc.perform(get("/test/board").param("boardType", "CUSTOMER_SERVICE"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("href=\"/test/board?boardType=COMMUNITY\"")))
            .andExpect(content().string(containsString("href=\"/test/board?boardType=PRODUCT_REVIEW\"")))
            .andExpect(content().string(containsString("href=\"/test/board?boardType=PRODUCT_INQUIRY\"")))
            .andExpect(content().string(containsString("class=\"board-tab active\" href=\"/test/board?boardType=CUSTOMER_SERVICE\"")))
            .andExpect(content().string(containsString("고객센터 글")))
            .andExpect(content().string(not(containsString("boardType=LEGACY"))));
    }

    @Test
    void defaultIsCommunity() throws Exception {
        mockMvc.perform(get("/test/board"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("class=\"board-tab active\" href=\"/test/board?boardType=COMMUNITY\"")))
            .andExpect(content().string(containsString("const boardType = 'COMMUNITY'")));
    }

    @Test
    void productBoards_requireAnItem_andHideNothingElse() throws Exception {
        mockMvc.perform(get("/test/board").param("boardType", "PRODUCT_INQUIRY"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("if (true && !itemIdValue)")))
            .andExpect(content().string(not(containsString("<p style=\"position: relative;\" hidden>"))))
            .andExpect(content().string(not(containsString("<label class=\"checkbox-row\" hidden>"))));
    }

    @Test
    void communityAndCustomerService_cannotLinkAnItem() throws Exception {
        for (String type : new String[]{"COMMUNITY", "CUSTOMER_SERVICE"}) {
            mockMvc.perform(get("/test/board").param("boardType", type))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<p style=\"position: relative;\" hidden>")))
                .andExpect(content().string(containsString("if (false && !itemIdValue)")));
        }
    }

    @Test
    void communityAndReview_hideTheSecretCheckbox() throws Exception {
        for (String type : new String[]{"COMMUNITY", "PRODUCT_REVIEW"}) {
            mockMvc.perform(get("/test/board").param("boardType", type))
                .andExpect(content().string(containsString("<label class=\"checkbox-row\" hidden>")));
        }
        mockMvc.perform(get("/test/board").param("boardType", "CUSTOMER_SERVICE"))
            .andExpect(content().string(not(containsString("<label class=\"checkbox-row\" hidden>"))));
    }
}
