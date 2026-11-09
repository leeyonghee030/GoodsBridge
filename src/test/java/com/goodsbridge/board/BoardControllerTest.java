package com.goodsbridge.board;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.GlobalExceptionHandler;
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
import static org.mockito.BDDMockito.willDoNothing; // 👈 반환값이 없는 수정/삭제 Mocking용
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
            .setControllerAdvice(new GlobalExceptionHandler()) // 👈 글로벌 예외 핸들러 등록!
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
    @DisplayName("게시글 단건 조회 성공")
    void getBoard_success() throws Exception {
        // given
        Long boardId = 1L;
        BoardResponseDto responseDto = BoardResponseDto.builder()
            .id(boardId)
            .title("테스트 제목")
            .content("테스트 내용")
            .build();

        // 💡 서비스 메서드가 인자를 2개(boardId, loginUserId) 받으므로 두 번째 인자도 eq(null) 등으로 맞춰줍니다.
        given(boardService.getBoard(eq(boardId), eq(null))).willReturn(responseDto);

        // when & then
        mockMvc.perform(get("/api/boards/{id}", boardId))
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

    @Test
    @DisplayName("게시글 수정 성공")
    void updateBoard_success() throws Exception {
        // given
        Long boardId = 1L;
        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("수정된 제목")
            .content("수정된 내용")
            .isSecret(false)
            .build();

        // 서비스 메서드가 void를 반환하는 경우 willDoNothing()을 사용합니다.
        willDoNothing().given(boardService).updateBoard(eq(boardId), any(BoardRequestDto.class), any());

        // when & then
        mockMvc.perform(put("/api/boards/{id}", boardId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andDo(print())
            .andExpect(status().isOk()); // 혹은 프로젝트 정책에 따라 isNoContent() 사용 가능
    }

    @Test
    @DisplayName("게시글 삭제 성공")
    void deleteBoard_success() throws Exception {
        // given
        Long boardId = 1L;
        willDoNothing().given(boardService).deleteBoard(eq(boardId), any());

        // when & then
        mockMvc.perform(delete("/api/boards/{id}", boardId))
            .andDo(print())
            .andExpect(status().isNoContent()); // 👈 status().isOk() 에서 변경 (204 No Content 검증)
    }

    @Test
    @DisplayName("게시글 작성 실패 - 비로그인 사용자 접근 시 예외 발생 검증")
    void createBoard_unauthorized_fail() throws Exception {
        // given
        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("테스트 제목")
            .content("테스트 내용")
            .isSecret(false)
            .build();

        // 비로그인 상태(userId 자리에 null 또는 비인증 상태)일 때 서비스에서 예외를 던지도록 설정
        given(boardService.createBoard(any(BoardRequestDto.class), eq(null)))
            .willThrow(new BadRequestException("로그인이 필요합니다."));

        // when & then
        mockMvc.perform(post("/api/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andDo(print())
            .andExpect(status().isBadRequest()); // 프로젝트 예외 핸들러 처리에 맞게 상태 코드 지정 (예: 400 또는 401/500)
    }
}
