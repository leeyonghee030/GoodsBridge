package com.goodsbridge.board;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        // 복잡한 수동 컨버터나 모듈 등록을 제거하고,
        // MockMvcBuilders가 기본 제공하는 메시지 컨버터 체인을 사용합니다.
        mockMvc = MockMvcBuilders.standaloneSetup(boardController)
            .setCustomArgumentResolvers(
                new PageableHandlerMethodArgumentResolver(),
                new AuthenticationPrincipalArgumentResolver()
            )
            .build();
    }

    @Test
    @DisplayName("게시글 목록 조회 성공 (페이징 및 검색 파라미터 지원)")
    void getBoards_success() throws Exception {
        // given
        PageImpl<BoardResponseDto> boardPage =
            new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        given(boardService.getBoards(eq("공지"), any(Pageable.class))).willReturn(boardPage);

        // when & then
        mockMvc.perform(get("/api/boards")
                .param("keyword", "공지")
                .param("page", "0")
                .param("size", "10"))
            .andDo(print())
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("게시글 작성 성공 (201 Created)")
    void createBoard_success() throws Exception {
        // given
        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("테스트 제목")
            .content("테스트 내용")
            .isSecret(false)
            .build();

        given(boardService.createBoard(any(BoardRequestDto.class), eq(null))).willReturn(1L);

        // when & then
        mockMvc.perform(post("/api/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andDo(print())
            .andExpect(status().isCreated());
    }
}
