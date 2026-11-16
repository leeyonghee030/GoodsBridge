
package com.goodsbridge.board;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodsbridge._core.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BoardControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BoardService boardService;

    @InjectMocks
    private BoardController boardController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(boardController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(
                new PageableHandlerMethodArgumentResolver(),
                new AuthenticationPrincipalArgumentResolver()
            )
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // 테스트용 로그인 사용자 설정
    private void login(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of()
            )
        );
    }

    @Test
    @DisplayName("게시글 목록 조회 성공 - 페이징 및 검색")
    void getBoards_success() throws Exception {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        PageImpl<BoardResponseDto> boardPage =
            new PageImpl<>(List.of(), pageable, 0);

        given(boardService.getBoards(
            eq("공지"),
            any(Pageable.class),
            isNull()
        )).willReturn(boardPage);

        // when & then
        mockMvc.perform(get("/api/boards")
                .param("keyword", "공지")
                .param("page", "0")
                .param("size", "10"))
            .andDo(print())
            .andExpect(status().isOk());

        verify(boardService).getBoards(
            eq("공지"),
            any(Pageable.class),
            isNull()
        );
    }

    @Test
    @DisplayName("게시글 단건 조회 성공")
    void getBoard_success() throws Exception {
        // given
        Long boardId = 1L;

        BoardResponseDto responseDto = BoardResponseDto.builder()
            .id(boardId)
            .title("테스트 제목")
            .content("테스트 내용")
            .build();

        given(boardService.getBoard(eq(boardId), isNull()))
            .willReturn(responseDto);

        // when & then
        mockMvc.perform(get("/api/boards/{id}", boardId))
            .andDo(print())
            .andExpect(status().isOk());

        verify(boardService).getBoard(boardId, null);
    }

    @Test
    @DisplayName("게시글 작성 성공 - 201 Created")
    void createBoard_success() throws Exception {
        // given
        Long loginUserId = 10L;
        login(loginUserId);

        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("테스트 제목")
            .content("테스트 내용")
            .boardType(BoardType.PRODUCT_INQUIRY)
            .itemId(100L)
            .isSecret(false)
            .build();

        given(boardService.createBoard(
            any(BoardRequestDto.class),
            eq(loginUserId)
        )).willReturn(1L);

        // when & then
        mockMvc.perform(post("/api/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andDo(print())
            .andExpect(status().isCreated());

        verify(boardService).createBoard(
            any(BoardRequestDto.class),
            eq(loginUserId)
        );
    }

    @Test
    @DisplayName("게시글 수정 성공")
    void updateBoard_success() throws Exception {
        // given
        Long boardId = 1L;
        Long loginUserId = 10L;
        login(loginUserId);

        BoardUpdateRequestDto requestDto = BoardUpdateRequestDto.builder()
            .title("수정된 제목")
            .content("수정된 내용")
            .isSecret(false)
            .build();

        willDoNothing().given(boardService)
            .updateBoard(
                eq(boardId),
                any(BoardUpdateRequestDto.class),
                eq(loginUserId)
            );

        // when & then
        mockMvc.perform(put("/api/boards/{id}", boardId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andDo(print())
            .andExpect(status().isOk());

        verify(boardService).updateBoard(
            eq(boardId),
            any(BoardUpdateRequestDto.class),
            eq(loginUserId)
        );
    }

    @Test
    @DisplayName("게시글 수정 실패 - 제목이 비어 있으면 400 Bad Request")
    void updateBoard_validation_fail() throws Exception {
        // given
        Long boardId = 1L;
        Long loginUserId = 10L;

        login(loginUserId);

        String requestJson = """
        {
            "title": "",
            "content": "수정된 내용",
            "isSecret": false
        }
        """;

        // when & then
        mockMvc.perform(put("/api/boards/{id}", boardId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andDo(print())
            .andExpect(status().isBadRequest());

        verify(boardService, never()).updateBoard(
            anyLong(),
            any(BoardUpdateRequestDto.class),
            anyLong()
        );
    }

    @Test
    @DisplayName("게시글 삭제 성공 - 204 No Content")
    void deleteBoard_success() throws Exception {
        // given
        Long boardId = 1L;
        Long loginUserId = 10L;
        login(loginUserId);

        willDoNothing().given(boardService)
            .deleteBoard(eq(boardId), eq(loginUserId));

        // when & then
        mockMvc.perform(delete("/api/boards/{id}", boardId))
            .andDo(print())
            .andExpect(status().isNoContent());

        verify(boardService).deleteBoard(boardId, loginUserId);
    }

    @Test
    @DisplayName("댓글 작성 성공 - 201 Created")
    void createReply_success() throws Exception {
        // given
        Long boardId = 1L;
        Long loginUserId = 10L;
        Long replyId = 100L;

        login(loginUserId);

        String requestJson = """
            {
                "content": "테스트 댓글입니다."
            }
            """;

        given(boardService.createReply(
            eq(boardId),
            any(ReplyRequestDto.class),
            eq(loginUserId)
        )).willReturn(replyId);

        // when & then
        mockMvc.perform(post("/api/boards/{id}/replies", boardId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andDo(print())
            .andExpect(status().isCreated());

        verify(boardService).createReply(
            eq(boardId),
            any(ReplyRequestDto.class),
            eq(loginUserId)
        );
    }

    @Test
    @DisplayName("댓글 삭제 성공 - 204 No Content")
    void deleteReply_success() throws Exception {
        // given
        Long replyId = 100L;
        Long loginUserId = 10L;

        login(loginUserId);

        willDoNothing().given(boardService)
            .deleteReply(eq(replyId), eq(loginUserId));

        // when & then
        mockMvc.perform(delete("/api/replies/{id}", replyId))
            .andDo(print())
            .andExpect(status().isNoContent());

        verify(boardService).deleteReply(replyId, loginUserId);
    }

    @Test
    @DisplayName("댓글 작성 실패 - 댓글 내용이 비어 있으면 400 Bad Request")
    void createReply_validation_fail() throws Exception {
        // given
        Long boardId = 1L;
        Long loginUserId = 10L;

        login(loginUserId);

        String requestJson = """
            {
                "content": ""
            }
            """;

        // when & then
        mockMvc.perform(post("/api/boards/{id}/replies", boardId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }
}
