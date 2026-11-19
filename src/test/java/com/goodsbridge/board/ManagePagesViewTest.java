package com.goodsbridge.board;

import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 마이페이지 "내 문의", 판매자 "문의 관리"·"리뷰 관리" 화면과, 기존 마이페이지·판매자 페이지의 탭 링크
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ManagePagesViewTest {

    @Autowired WebApplicationContext context;
    @Autowired ShopRepository shopRepository;
    MockMvc mockMvc;
    Long ownerId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        Shop shop = shopRepository.findAll().get(0);
        ownerId = shop.getOwnerUserId();
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @Test
    void myInquiries_pageShowsTheUsersInquiryList() throws Exception {
        loginAs(999_999L);

        mockMvc.perform(get("/my/inquiries"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<a href=\"/my/inquiries\" class=\"active\">내 문의</a>")))
            .andExpect(content().string(containsString("const SCOPE = 'mine';")))
            .andExpect(content().string(containsString("/api/boards/my-inquiries")))
            .andExpect(content().string(containsString("id=\"mb-list\"")))
            // 상품/이벤트/경매로 나눠 보는 탭과 답변 상태 걸러보기
            .andExpect(content().string(containsString("['EVENT', '이벤트']")))
            .andExpect(content().string(containsString("['COMPLETED', '답변 완료']")));
    }

    @Test
    void shopInquiries_pageIsForShopOwners() throws Exception {
        loginAs(ownerId);

        mockMvc.perform(get("/shop/inquiries"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<a href=\"/shop/inquiries\" class=\"active\">문의 관리</a>")))
            .andExpect(content().string(containsString("const SCOPE = 'seller';")))
            .andExpect(content().string(containsString("<a href=\"/shop/reviews\">리뷰 관리</a>")));
    }

    @Test
    void shopReviews_pageHasTheHideAndDeleteActions() throws Exception {
        loginAs(ownerId);

        mockMvc.perform(get("/shop/reviews"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<a href=\"/shop/reviews\" class=\"active\">리뷰 관리</a>")))
            .andExpect(content().string(containsString("/api/boards/seller/reviews")))
            .andExpect(content().string(containsString("data-act=\"toggle\"")))
            .andExpect(content().string(containsString("data-act=\"delete\"")))
            .andExpect(content().string(containsString("/hidden")));
    }

    @Test
    void shopPages_redirectNonShopOwnersToMyInfo() throws Exception {
        loginAs(999_999L);

        mockMvc.perform(get("/shop/inquiries")).andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "/update"));
        mockMvc.perform(get("/shop/reviews")).andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "/update"));
    }

    @Test
    void existingMyPages_haveTheNewTab() throws Exception {
        loginAs(999_999L);

        mockMvc.perform(get("/my/purchases"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<a href=\"/my/inquiries\">내 문의</a>")));
    }

    @Test
    void existingShopPages_haveTheNewTabs() throws Exception {
        loginAs(ownerId);

        mockMvc.perform(get("/shop/items"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<a href=\"/shop/inquiries\">문의 관리</a>")))
            .andExpect(content().string(containsString("<a href=\"/shop/reviews\">리뷰 관리</a>")));
    }
}
