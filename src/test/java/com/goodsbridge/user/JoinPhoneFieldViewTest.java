package com.goodsbridge.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 가입 화면(/join)에는 헤더의 가입 팝업과 가입 페이지의 입력칸이 함께 들어간다.
 * 둘이 같은 id 를 쓰면 스크립트(getElementById)가 첫 번째 칸에만 붙고, 팝업이 필수로 막는 일이 생긴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JoinPhoneFieldViewTest {

    @Autowired MockMvc mockMvc;

    private String page(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString();
    }

    private static int count(String html, String text) {
        Matcher m = Pattern.compile(Pattern.quote(text)).matcher(html);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    @Test
    void joinPage_hasEachPhoneIdOnlyOnce() throws Exception {
        String html = page("/join");

        assertThat(count(html, "id=\"phoneNumber\"")).as("가입 페이지의 입력칸").isEqualTo(1);
        assertThat(count(html, "id=\"signup-phone\"")).as("헤더 가입 팝업의 입력칸").isEqualTo(1);
    }

    @Test
    void signupPopupPhone_isOptionalAndStillSentAsPhoneNumber() throws Exception {
        String html = page("/");
        int start = html.indexOf("id=\"signup-phone\"");
        assertThat(start).isPositive();
        String tag = html.substring(start, html.indexOf('>', start));

        assertThat(tag).contains("name=\"phoneNumber\"");   // 서버(JoinDto.phoneNumber)로 가는 이름은 그대로
        assertThat(tag).doesNotContain("required");          // 화면에는 "(선택)" 이라고 적혀 있다
    }
}
