package com.goodsbridge.board;

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

/** 게시글 상세 화면: 목록 링크는 그 글의 유형으로, 수정 폼은 유형 규칙에 맞게 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BoardDetailViewTest {

    @Autowired WebApplicationContext context;
    @Autowired BoardRepository boardRepository;
    @Autowired UserRepository userRepository;
    MockMvc mockMvc;
    Long authorId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        authorId = userRepository.save(User.builder().email("detail@view.test").password("pw")
            .nickname("상세작성자").loginType(LoginType.EMAIL).build()).getId();
        // 고객센터·상품 문의 글은 작성자만 볼 수 있으므로 작성자로 로그인한 상태로 요청한다
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(authorId, null, List.of()));
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    private Long board(BoardType type) {
        return boardRepository.save(Board.builder().title("제목").content("내용").authorId(authorId)
            .boardType(type).itemId(null).isSecret(false).build()).getId();
    }

    @Test
    void backLinkGoesToTheListOfTheSameType() throws Exception {
        for (BoardType type : new BoardType[]{BoardType.COMMUNITY, BoardType.CUSTOMER_SERVICE}) {
            mockMvc.perform(get("/test/board/" + board(type)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                    "href=\"/test/board?boardType=" + type.name() + "\" class=\"back-link\"")));
        }
    }

    @Test
    void backLinkReturnsToTheScreenItCameFrom_onlyForLocalPaths() throws Exception {
        Long id = board(BoardType.CUSTOMER_SERVICE);
        // 구매 내역의 "내 리뷰"처럼 들어온 화면을 넘기면 그 화면으로 돌아간다
        mockMvc.perform(get("/test/board/" + id).param("from", "/my/purchases?tab=CONFIRMED"))
            .andExpect(content().string(containsString(
                "href=\"/my/purchases?tab=CONFIRMED\" class=\"back-link\"")));
        // 외부 주소나 HTML 을 깨는 값은 무시하고 기본 목록으로 간다
        for (String bad : new String[]{"https://evil.test", "//evil.test", "/x\" onclick=\"alert(1)", "javascript:alert(1)"}) {
            mockMvc.perform(get("/test/board/" + id).param("from", bad))
                .andExpect(content().string(containsString(
                    "href=\"/test/board?boardType=CUSTOMER_SERVICE\" class=\"back-link\"")));
        }
    }

    @Test
    void deleteGoesBackToTheSameScreenAsTheBackLink() throws Exception {
        mockMvc.perform(get("/test/board/" + board(BoardType.COMMUNITY)))
            .andExpect(content().string(containsString("location.href = document.querySelector('.back-link').getAttribute('href');")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("location.href = '/test/board';"))));
    }

    @Test
    void editForm_neverOffersChangingTheLinkedItem() throws Exception {
        mockMvc.perform(get("/test/board/" + board(BoardType.CUSTOMER_SERVICE)))
            .andExpect(content().string(containsString("<p style=\"position: relative;\" hidden>")));
    }

    @Test
    void editForm_hasNoSecretCheckbox() throws Exception {
        for (BoardType type : new BoardType[]{BoardType.COMMUNITY, BoardType.CUSTOMER_SERVICE}) {
            mockMvc.perform(get("/test/board/" + board(type)))
                .andExpect(content().string(not(containsString("edit-secret"))));
        }
    }

    @Test
    void lockedBoardsInTheList_areNotLinkedForOtherUsers() throws Exception {
        Long id = board(BoardType.CUSTOMER_SERVICE);
        // 작성자 본인에게는 링크가 보인다
        mockMvc.perform(get("/test/board").param("boardType", "CUSTOMER_SERVICE"))
            .andExpect(content().string(containsString("href=\"/test/board/" + id + "\"")));

        Long otherId = userRepository.save(User.builder().email("other@view.test").password("pw")
            .nickname("다른사용자").loginType(LoginType.EMAIL).build()).getId();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(otherId, null, List.of()));

        mockMvc.perform(get("/test/board").param("boardType", "CUSTOMER_SERVICE"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("작성자와 관리자만 볼 수 있어요")))
            .andExpect(content().string(not(containsString("href=\"/test/board/" + id + "\""))));
    }
}
