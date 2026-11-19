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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    void showsTheTwoTabs_andMarksTheCurrentOne() throws Exception {
        mockMvc.perform(get("/test/board").param("boardType", "CUSTOMER_SERVICE"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("href=\"/test/board?boardType=COMMUNITY\"")))
            // 상품 리뷰는 게시판이 아니라 각 상품 상세 화면에서 보여준다
            .andExpect(content().string(not(containsString("boardType=PRODUCT_REVIEW"))))
            // 상품 문의도 게시판이 아니라 상품·이벤트·경매 상세 화면과 마이페이지에서 다룬다
            .andExpect(content().string(not(containsString("boardType=PRODUCT_INQUIRY"))))
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
    void productReviewBoard_isNotShown_andGoesBackToCommunity() throws Exception {
        mockMvc.perform(get("/test/board").param("boardType", "PRODUCT_REVIEW"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "/test/board"));
    }

    @Test
    void productInquiryBoard_isNotShown_andGoesBackToCommunity() throws Exception {
        mockMvc.perform(get("/test/board").param("boardType", "PRODUCT_INQUIRY"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "/test/board"));
    }

    @Test
    void writeForm_hasNoItemPicker_andNoSecretCheckbox() throws Exception {
        for (String type : new String[]{"COMMUNITY", "CUSTOMER_SERVICE"}) {
            mockMvc.perform(get("/test/board").param("boardType", type))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"item-search\""))))
                .andExpect(content().string(not(containsString("board-secret"))));
        }
    }

    @Test
    void customerService_showsTheOnlyAuthorAndAdminNote() throws Exception {
        mockMvc.perform(get("/test/board").param("boardType", "CUSTOMER_SERVICE"))
            .andExpect(content().string(containsString("작성자·관리자만 볼 수 있어요")));
        mockMvc.perform(get("/test/board").param("boardType", "COMMUNITY"))
            .andExpect(content().string(not(containsString("작성자·관리자만 볼 수 있어요"))));
    }
}
