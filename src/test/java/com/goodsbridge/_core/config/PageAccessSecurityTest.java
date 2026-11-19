package com.goodsbridge._core.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 관리자 화면·마이페이지는 로그인이 있어야 열리고, 오류 응답에는 스택트레이스를 담지 않는다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PageAccessSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired Environment environment;

    // 이 앱의 인증 객체는 principal 이 사용자 id(Long) 이다 (JwtAuthenticationFilter 와 같은 모양)
    private RequestPostProcessor loggedIn(String role) {
        return authentication(new UsernamePasswordAuthenticationToken(
            1L, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void anonymousBrowser_isSentToHomeWithLoginPopup() throws Exception {
        for (String page : new String[]{"/my/purchases", "/my/exchange", "/test/admin", "/test/admin/shops", "/update"}) {
            mockMvc.perform(get(page).header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "/?login=open"));
        }
    }

    @Test
    void anonymousNonBrowserRequests_stillGet403() throws Exception {
        // Accept 가 text/html 이 아닌 요청(API 클라이언트 등)과 /api 경로는 리다이렉트하지 않는다
        mockMvc.perform(get("/my/purchases")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/purchases/me").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    void adminPages_requireAdminRole() throws Exception {
        mockMvc.perform(get("/test/admin").with(loggedIn("USER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/test/admin/shops").with(loggedIn("USER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/test/admin").with(loggedIn("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(get("/test/admin/shops").with(loggedIn("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void myPages_openForLoggedInUsers() throws Exception {
        mockMvc.perform(get("/my/purchases").with(loggedIn("USER"))).andExpect(status().isOk());
    }

    @Test
    void publicPages_stayOpen() throws Exception {
        for (String page : new String[]{"/", "/test/board", "/test/item", "/test/shop", "/test/auction", "/exchange"}) {
            mockMvc.perform(get(page).header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
                .andExpect(status().isOk());
        }
    }

    @Test
    void errorResponses_neverIncludeStackTrace() {
        // devtools(bootRun/IDE)는 기본값을 always 로 바꾸므로 명시 설정이 있어야 한다
        assertEquals("never", environment.getProperty("server.error.include-stacktrace"));
    }
}
