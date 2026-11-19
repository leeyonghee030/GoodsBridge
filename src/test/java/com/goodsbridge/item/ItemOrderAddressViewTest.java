package com.goodsbridge.item;

import com.goodsbridge.user.Address;
import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 구매 창의 배송지 주소: 내 정보와 같은 방식(우편번호 검색 + 기본주소 + 상세주소)이고,
 * 내 정보에 기본 주소가 있으면 "기본 주소 / 새로운 주소"를 고를 수 있게 기본 주소를 넘겨준다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ItemOrderAddressViewTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository userRepository;
    @Autowired ItemRepository itemRepository;

    MockMvc mockMvc;
    Long itemId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        itemId = itemRepository.findAll().get(0).getId();
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Address address) {
        Long userId = userRepository.save(User.builder().email("order-address@view.test").password("pw")
            .nickname("주문자").loginType(LoginType.EMAIL).address(address).build()).getId();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @Test
    void addressUsesThePostcodeSearchLikeMyProfile() throws Exception {
        loginAs(null);
        mockMvc.perform(get("/test/item/" + itemId))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"da-postcode\"")))
            .andExpect(content().string(containsString("id=\"da-search\"")))
            .andExpect(content().string(containsString("id=\"da-base\"")))
            .andExpect(content().string(containsString("id=\"da-detail\"")))
            .andExpect(content().string(containsString("postcode.v2.js")))
            .andExpect(content().string(not(containsString("id=\"order-address\""))));
    }

    @Test
    void userWithoutDefaultAddress_hasNoDefaultOrNewChoice() throws Exception {
        loginAs(null);
        mockMvc.perform(get("/test/item/" + itemId))
            .andExpect(content().string(not(containsString("id=\"da-modes\""))));
    }

    @Test
    void userWithDefaultAddress_startsWithItAndCanChooseANewOne() throws Exception {
        loginAs(Address.builder().postalCode("04001").address("서울 마포구 와우산로 21").detailAddress("3층").build());
        mockMvc.perform(get("/test/item/" + itemId))
            .andExpect(content().string(containsString("id=\"da-modes\"")))
            .andExpect(content().string(containsString("data-postal-code=\"04001\"")))
            .andExpect(content().string(containsString("data-address=\"서울 마포구 와우산로 21\"")))
            .andExpect(content().string(containsString("data-detail-address=\"3층\"")))
            .andExpect(content().string(containsString("data-mode=\"default\" class=\"active\">기본 주소</button>")))
            .andExpect(content().string(containsString("data-mode=\"new\">새로운 주소</button>")));
    }

    @Test
    void auctionBalancePayment_usesTheSameAddressInputWithTheDefaultAddress() throws Exception {
        loginAs(Address.builder().postalCode("04001").address("서울 마포구 와우산로 21").detailAddress("3층").build());
        for (String page : new String[]{"/test/auction/1", "/my/auctions"}) {
            mockMvc.perform(get(page))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"da-search\"")))
                .andExpect(content().string(containsString("data-address=\"서울 마포구 와우산로 21\"")))
                .andExpect(content().string(containsString("window.deliveryAddress.text()")))
                .andExpect(content().string(not(containsString("-address\" rows=\"2\""))));
        }
    }

    @Test
    void emptyAddressIsNotADefaultAddress() throws Exception {
        loginAs(Address.builder().postalCode("").address(" ").detailAddress("").build());
        mockMvc.perform(get("/test/item/" + itemId))
            .andExpect(content().string(not(containsString("id=\"da-modes\""))));
    }

    @Test
    void guestSeesThePageWithoutADefaultAddress() throws Exception {
        mockMvc.perform(get("/test/item/" + itemId))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("id=\"da-modes\""))));
    }
}
