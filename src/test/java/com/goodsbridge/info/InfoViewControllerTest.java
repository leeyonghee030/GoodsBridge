package com.goodsbridge.info;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InfoViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // 푸터에서 연결되는 안내 화면은 로그인하지 않아도 열려야 한다 (로그인이 필요하면 401/302로 막힌다)
    @Test
    void infoPagesOpenWithoutLoggingIn() throws Exception {
        mockMvc.perform(get("/about")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("GoodsBridge 소개")));
        mockMvc.perform(get("/shop-guide")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("입점 문의")));
        mockMvc.perform(get("/notice")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("공지사항")));
        mockMvc.perform(get("/faq")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("자주 묻는 질문")));
    }

    // "문의를 남겨 주세요" 안내는 공개 커뮤니티가 아니라 고객센터 게시판으로 가야 한다. (푸터의 기본 "게시판" 링크는 그대로 둔다)
    @Test
    void inquiryLinksGoToCustomerServiceBoard() throws Exception {
        String customerService = "href=\"/test/board?boardType=CUSTOMER_SERVICE\"";
        for (String page : new String[]{"/faq", "/shop-guide", "/notice"}) {
            mockMvc.perform(get(page))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(customerService)));
        }
    }
}
