package com.goodsbridge.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 로그인 창(fetch)이 실패를 알 수 있어야 한다: 리다이렉트(3xx)가 아니라 400 + 에러 메시지(JSON)여야 한다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WithdrawnEmailLoginTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void 탈퇴한_계정으로_이메일_로그인하면_400과_불일치_메시지를_돌려준다() throws Exception {
        User user = userRepository.save(User.builder()
            .email("withdrawn-login@test.com")
            .password(passwordEncoder.encode("pw1234!!"))
            .nickname("탈퇴자")
            .loginType(LoginType.EMAIL)
            .build());
        user.withdraw();
        userRepository.flush();

        mockMvc.perform(post("/api/login")
                .param("email", "withdrawn-login@test.com")
                .param("password", "pw1234!!"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("사용자명 또는 비밀번호가 올바르지 않습니다"));
    }
}
