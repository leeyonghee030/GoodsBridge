package com.goodsbridge.board;

import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 커뮤니티·상품 리뷰 글은 답변 상태(status)가 null 이다.
 * 목록 화면이 null status 에서 렌더링이 중간에 멈춰 페이지가 잘리던 문제를 막는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BoardListRenderTest {

    @Autowired WebApplicationContext context;
    @Autowired BoardRepository boardRepository;
    @Autowired UserRepository userRepository;
    MockMvc mockMvc;
    Long authorId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        authorId = userRepository.save(User.builder().email("render@list.test").password("pw")
            .nickname("렌더작성자").loginType(LoginType.EMAIL).build()).getId();
    }

    private void community(String title) {
        boardRepository.save(Board.builder().title(title).content("내용").authorId(authorId)
            .boardType(BoardType.COMMUNITY).itemId(null).isSecret(false).build());
    }

    @Test
    void communityList_rendersEveryRowAndTheWholePage() throws Exception {
        community("첫번째 글");
        community("두번째 글");

        mockMvc.perform(get("/test/board").param("boardType", "COMMUNITY"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("첫번째 글")))
            .andExpect(content().string(containsString("두번째 글")))
            // 페이지가 끝까지 렌더링됐다는 증거: 목록 뒤의 스크립트와 닫는 태그
            .andExpect(content().string(containsString("const boardType = 'COMMUNITY'")))
            .andExpect(content().string(containsString("</html>")))
            // 상태가 없는 글의 행에는 "null" 이나 답변 상태 문구 없이 작성자만 나온다
            .andExpect(content().string(containsString("작성자: 렌더작성자</small>")))
            .andExpect(content().string(not(containsString("작성자: 렌더작성자 ·"))));
    }

    @Test
    void replyBoards_stillShowTheirStatus() throws Exception {
        boardRepository.save(Board.builder().title("문의").content("내용").authorId(authorId)
            .boardType(BoardType.CUSTOMER_SERVICE).itemId(null).isSecret(false).build());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                authorId, null, java.util.List.of()));
        try {
            mockMvc.perform(get("/test/board").param("boardType", "CUSTOMER_SERVICE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("답변 대기")))
                .andExpect(content().string(containsString("</html>")));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
