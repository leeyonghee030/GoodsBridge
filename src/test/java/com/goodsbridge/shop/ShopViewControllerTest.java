package com.goodsbridge.shop;

import com.goodsbridge._core.config.SecurityConfig;
import com.goodsbridge._core.security.JwtProvider;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(ShopViewController.class)
@Import({SecurityConfig.class, ShopViewControllerTest.TestConfig.class})
class ShopViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShopSubscriptionService shopSubscriptionService;

    @Autowired
    private UserRepository userRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ShopService shopService() {
            return mock(ShopService.class);
        }

        @Bean
        public ShopSubscriptionService shopSubscriptionService() {
            return mock(ShopSubscriptionService.class);
        }

        @Bean
        public UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        public JwtProvider jwtProvider() {
            return mock(JwtProvider.class);
        }
    }

    @Test
    @DisplayName("비로그인 유저가 만료 구독 배치 수동 트리거 호출 시 403(Forbidden) 발생")
    void triggerBatch_WithoutLogin_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/admin/scheduler/subscriptions"))
            .andExpect(status().isForbidden()); // 💡 401 대신 403으로 수정
    }

    @Test
    @DisplayName("일반 유저(ROLE_USER)가 만료 구독 배치 수동 트리거 호출 시 403(Forbidden) 발생")
    @WithMockUser(username = "1", roles = "USER")
    void triggerBatch_AsNormalUser_Forbidden() throws Exception {
        User normalUser = User.builder()
            .email("user@test.com")
            .nickname("normalUser")
            .build();

        given(userRepository.findById(anyLong())).willReturn(Optional.of(normalUser));

        mockMvc.perform(post("/api/admin/scheduler/subscriptions"))
            .andExpect(status().isForbidden()); // 시큐리티 또는 서비스에서 403 차단되어야 함
    }

    @Test
    @DisplayName("관리자 유저(ROLE_ADMIN)가 만료 구독 배치 수동 트리거 호출 시 200(OK) 성공")
    @WithMockUser(username = "1", roles = "ADMIN")
    void triggerBatch_AsAdmin_Success() throws Exception {
        User adminUser = User.builder()
            .email("admin@test.com")
            .nickname("adminUser")
            .build();

        // 💡 팀원 엔티티의 빌더는 role을 무조건 USER로 만드므로,
        // Reflection을 통해 테스트용 관리자 권한(Role.ADMIN)을 강제 주입합니다.
        ReflectionTestUtils.setField(adminUser, "role", Role.ADMIN);

        given(userRepository.findById(anyLong())).willReturn(Optional.of(adminUser));
        given(shopSubscriptionService.processExpiredSubscriptionsByAdmin()).willReturn(5);

        mockMvc.perform(post("/api/admin/scheduler/subscriptions"))
            .andExpect(status().isOk());
    }
}
