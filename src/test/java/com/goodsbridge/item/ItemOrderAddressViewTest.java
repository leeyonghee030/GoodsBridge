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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 구매·낙찰 잔금 결제 창의 배송지 주소: 내 정보와 같은 방식(우편번호 검색 + 기본주소 + 상세주소).
 * 내 기본 주소는 화면을 그릴 때 미리 넣지 않고, 창을 열 때 /api/users/me/default-address 로 받아 온다.
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
    void paymentScreensUseThePostcodeSearchAndLoadTheDefaultAddressOnlyWhenOpened() throws Exception {
        loginAs(Address.builder().postalCode("04001").address("서울 마포구 와우산로 21").detailAddress("3층").build());
        for (String page : new String[]{"/test/item/" + itemId, "/test/auction/1", "/my/auctions"}) {
            mockMvc.perform(get(page))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"da-search\"")))
                .andExpect(content().string(containsString("id=\"da-detail\"")))
                .andExpect(content().string(containsString("<script async src=\"//t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js\">")))
                .andExpect(content().string(containsString("/js/postcode-address.js")))
                .andExpect(content().string(containsString("/api/users/me/default-address")))
                // 선택 버튼은 기본 주소를 받아 온 뒤에만 보인다 (화면에 주소를 미리 넣지 않는다)
                .andExpect(content().string(containsString("<div class=\"da-modes\" id=\"da-modes\" hidden>")))
                .andExpect(content().string(not(containsString("서울 마포구 와우산로 21"))))
                // 구매할 때 상세주소는 필수
                .andExpect(content().string(containsString("상세주소(동·호수 등)를 입력해 주세요.")));
        }
    }

    @Test
    void itemOrderOpensWithTheDefaultAddressReset() throws Exception {
        loginAs(null);
        mockMvc.perform(get("/test/item/" + itemId))
            .andExpect(content().string(containsString("window.deliveryAddress.reset(); // 내 기본 주소는 창을 열 때 받아 온다")))
            .andExpect(content().string(not(containsString("id=\"order-address\""))));
    }

    @Test
    void defaultAddressApi_returnsMySavedAddress() throws Exception {
        loginAs(Address.builder().postalCode("04001").address("서울 마포구 와우산로 21").detailAddress("3층").build());
        mockMvc.perform(get("/api/users/me/default-address"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.postalCode").value("04001"))
            .andExpect(jsonPath("$.address").value("서울 마포구 와우산로 21"))
            .andExpect(jsonPath("$.detailAddress").value("3층"));
    }

    @Test
    void defaultAddressApi_isEmptyWithoutASavedAddress() throws Exception {
        loginAs(null);
        mockMvc.perform(get("/api/users/me/default-address")).andExpect(status().isNoContent());
    }

    @Test
    void defaultAddressApi_treatsABlankAddressAsNone() throws Exception {
        loginAs(Address.builder().postalCode("").address(" ").detailAddress("").build());
        mockMvc.perform(get("/api/users/me/default-address")).andExpect(status().isNoContent());
    }

    @Test
    void myProfileUsesTheSameAddressRules() throws Exception {
        loginAs(null);
        mockMvc.perform(get("/update"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/js/postcode-address.js")))
            .andExpect(content().string(containsString("window.formatPostcodeAddress(data)")));
    }
}
