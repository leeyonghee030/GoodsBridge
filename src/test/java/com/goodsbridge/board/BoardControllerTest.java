
package com.goodsbridge.board;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodsbridge._core.config.WebMvcConfig;
import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
        PageableHandlerMethodArgumentResolver pageableResolver =
            new PageableHandlerMethodArgumentResolver();

        // 실제 WebMvcConfig의 최대 페이지 크기 설정 적용
        new WebMvcConfig()
            .pageableCustomizer()
            .customize(pageableResolver);

        mockMvc = MockMvcBuilders
            .standaloneSetup(boardController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(
                pageableResolver,
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

    // =========================
    // 게시글 목록 조회
    // =========================

    @Test
    @DisplayName("게시글 목록 조회 성공 - 페이징 및 검색")
    void getBoards_success() throws Exception {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        PageImpl<BoardResponseDto> boardPage =
            new PageImpl<>(List.of(), pageable, 0);

        given(boardService.getBoards(
            eq(BoardType.COMMUNITY),
            eq("공지"),
            isNull(),
            isNull(),
            isNull(),
            any(Pageable.class),
            isNull()
        )).willReturn(boardPage);

        // when & then
        mockMvc.perform(get("/api/boards")
                .param("boardType", "COMMUNITY")
                .param("keyword", "공지")
                .param("page", "0")
                .param("size", "10"))
            .andDo(print())
            .andExpect(status().isOk());

        verify(boardService).getBoards(
            eq(BoardType.COMMUNITY),
            eq("공지"),
            isNull(),
            isNull(),
            isNull(),
            any(Pageable.class),
            isNull()
        );
    }

    @Test
    @DisplayName("게시판 유형을 지정하지 않으면 COMMUNITY 게시글을 조회한다")
    void getBoards_defaultBoardType_test() throws Exception {
        // given
        PageImpl<BoardResponseDto> boardPage =
            new PageImpl<>(
                List.of(),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id")),
                0
            );

        given(boardService.getBoards(
            eq(BoardType.COMMUNITY),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any(Pageable.class),
            isNull()
        )).willReturn(boardPage);

        // when & then
        mockMvc.perform(get("/api/boards"))
            .andDo(print())
            .andExpect(status().isOk());

        verify(boardService).getBoards(
            eq(BoardType.COMMUNITY),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any(Pageable.class),
            isNull()
        );
    }

    // =========================
    // 게시글 상세 조회
    // =========================

    @Test
    @DisplayName("게시글 단건 조회 성공 - 응답 데이터와 로그인 사용자 전달 검증")
    void getBoard_success() throws Exception {
        Long boardId = 1L;

        BoardResponseDto responseDto = BoardResponseDto.builder()
            .id(boardId)
            .title("테스트 제목")
            .content("테스트 내용")
            .build();

        given(boardService.getBoard(eq(boardId), isNull()))
            .willReturn(responseDto);

        mockMvc.perform(get("/api/boards/{id}", boardId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.title").value("테스트 제목"))
            .andExpect(jsonPath("$.content").value("테스트 내용"));

        verify(boardService, times(1)).getBoard(boardId, null);
    }

    // =========================
    // 게시글 생성
    // =========================

    @Test
    @DisplayName("게시글 작성 성공 - 생성된 ID와 요청 DTO 검증")
    void createBoard_success() throws Exception {
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

        mockMvc.perform(post("/api/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isCreated())
            .andExpect(content().string("1"));

        ArgumentCaptor<BoardRequestDto> captor =
            ArgumentCaptor.forClass(BoardRequestDto.class);

        verify(boardService, times(1)).createBoard(
            captor.capture(),
            eq(loginUserId)
        );

        BoardRequestDto savedRequest = captor.getValue();

        assertThat(savedRequest.getTitle()).isEqualTo("테스트 제목");
        assertThat(savedRequest.getContent()).isEqualTo("테스트 내용");
        assertThat(savedRequest.getBoardType()).isEqualTo(BoardType.PRODUCT_INQUIRY);
        assertThat(savedRequest.getItemId()).isEqualTo(100L);
        assertThat(savedRequest.getIsSecret()).isFalse();
    }

    // =========================
    // 게시글 수정
    // =========================

    @Test
    @DisplayName("게시글 수정 성공 - 게시글 ID, DTO, 로그인 사용자 전달 검증")
    void updateBoard_success() throws Exception {
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

        mockMvc.perform(put("/api/boards/{id}", boardId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        ArgumentCaptor<BoardUpdateRequestDto> captor =
            ArgumentCaptor.forClass(BoardUpdateRequestDto.class);

        verify(boardService, times(1)).updateBoard(
            eq(boardId),
            captor.capture(),
            eq(loginUserId)
        );

        assertThat(captor.getValue().getTitle()).isEqualTo("수정된 제목");
        assertThat(captor.getValue().getContent()).isEqualTo("수정된 내용");
        assertThat(captor.getValue().getIsSecret()).isFalse();
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

    // =========================
    // 게시글 삭제
    // =========================

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

    // =========================
    // 댓글 작성
    // =========================

    @Test
    @DisplayName("댓글 작성 성공 - 생성된 댓글 ID와 요청 DTO 검증")
    void createReply_success() throws Exception {
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

        mockMvc.perform(post("/api/boards/{id}/replies", boardId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isCreated())
            .andExpect(content().string("100"));

        ArgumentCaptor<ReplyRequestDto> captor =
            ArgumentCaptor.forClass(ReplyRequestDto.class);

        verify(boardService, times(1)).createReply(
            eq(boardId),
            captor.capture(),
            eq(loginUserId)
        );

        assertThat(captor.getValue().getContent())
            .isEqualTo("테스트 댓글입니다.");
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

        verify(boardService, never()).createReply(
            anyLong(),
            any(ReplyRequestDto.class),
            anyLong()
        );
    }

    // =========================
    // 댓글 삭제
    // =========================

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
    @DisplayName("비밀 상품 문의 생성 요청을 Service에 전달한다")
    void createSecretInquiryBoard_success() throws Exception {
        // given
        Long loginUserId = 10L;
        login(loginUserId);

        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("비밀 상품 문의")
            .content("비밀 문의 내용")
            .boardType(BoardType.PRODUCT_INQUIRY)
            .itemId(100L)
            .isSecret(true)
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
            argThat(dto ->
                dto.getBoardType() == BoardType.PRODUCT_INQUIRY
                    && dto.getItemId().equals(100L)
                    && Boolean.TRUE.equals(dto.getIsSecret())
            ),
            eq(loginUserId)
        );
    }

    @Test
    @DisplayName("로그인 사용자의 비밀 상품 문의 상세 조회 요청을 Service에 전달한다")
    void getSecretInquiryBoard_success() throws Exception {
        // given
        Long boardId = 1L;
        Long loginUserId = 10L;

        login(loginUserId);

        BoardResponseDto responseDto = BoardResponseDto.builder()
            .id(boardId)
            .title("비밀 상품 문의")
            .content("비밀 문의 내용")
            .build();

        given(boardService.getBoard(boardId, loginUserId))
            .willReturn(responseDto);

        // when & then
        mockMvc.perform(get("/api/boards/{id}", boardId))
            .andDo(print())
            .andExpect(status().isOk());

        verify(boardService).getBoard(boardId, loginUserId);
    }
    @Test
    @DisplayName("게시글 목록 조회 시 기본 페이징과 정렬 조건을 전달한다")
    void getBoards_defaultPageable_test() throws Exception {
        // given
        Pageable responsePageable = PageRequest.of(0, 10);

        PageImpl<BoardResponseDto> boardPage =
            new PageImpl<>(List.of(), responsePageable, 0);

        given(boardService.getBoards(
            eq(BoardType.COMMUNITY),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any(Pageable.class),
            isNull()
        )).willReturn(boardPage);

        // when
        mockMvc.perform(get("/api/boards"))
            .andDo(print())
            .andExpect(status().isOk());

        // then
        org.mockito.ArgumentCaptor<Pageable> captor =
            org.mockito.ArgumentCaptor.forClass(Pageable.class);

        verify(boardService).getBoards(
            eq(BoardType.COMMUNITY),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            captor.capture(),
            isNull()
        );

        Pageable pageable = captor.getValue();

        org.assertj.core.api.Assertions.assertThat(pageable.getPageNumber())
            .isEqualTo(0);

        org.assertj.core.api.Assertions.assertThat(pageable.getPageSize())
            .isEqualTo(10);

        org.assertj.core.api.Assertions.assertThat(pageable.getSort())
            .isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
    }
    @Test
    @DisplayName("잘못된 게시판 유형을 전달하면 400 Bad Request")
    void getBoards_invalidBoardType_fail() throws Exception {
        // when & then
        mockMvc.perform(get("/api/boards")
                .param("boardType", "INVALID_TYPE"))
            .andDo(print())
            .andExpect(status().isBadRequest());

        verifyNoInteractions(boardService);
    }
    @Test
    @DisplayName("게시글 ID가 숫자가 아니면 400 Bad Request")
    void getBoard_invalidId_fail() throws Exception {
        // when & then
        mockMvc.perform(get("/api/boards/{id}", "invalid"))
            .andDo(print())
            .andExpect(status().isBadRequest());

        verifyNoInteractions(boardService);
    }
    @Test
    @DisplayName("게시글 생성 실패 - 제목이 비어 있으면 400 Bad Request")
    void createBoard_validation_fail() throws Exception {
        Long loginUserId = 10L;
        login(loginUserId);

        String requestJson = """
        {
            "title": "",
            "content": "테스트 내용",
            "boardType": "COMMUNITY",
            "isSecret": false
        }
        """;

        mockMvc.perform(post("/api/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(boardService);
    }
    @Test
    @DisplayName("게시글 생성 중 잘못된 요청 예외가 발생하면 400을 반환한다")
    void createBoard_serviceException_fail() throws Exception {
        Long loginUserId = 10L;
        login(loginUserId);

        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("테스트 제목")
            .content("테스트 내용")
            .boardType(BoardType.COMMUNITY)
            .isSecret(false)
            .build();

        given(boardService.createBoard(
            any(BoardRequestDto.class),
            eq(loginUserId)
        )).willThrow(new BadRequestException("잘못된 게시글 요청입니다."));

        mockMvc.perform(post("/api/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isBadRequest());

        verify(boardService, times(1)).createBoard(
            any(BoardRequestDto.class),
            eq(loginUserId)
        );
    }

    @Test
    @DisplayName("게시글 목록 조회 시 요청한 페이지 크기를 그대로 전달한다")
    void getBoards_customPageSize_test() throws Exception {
        // given
        PageImpl<BoardResponseDto> boardPage =
            new PageImpl<>(
                List.of(),
                PageRequest.of(0, 20),
                0
            );

        given(boardService.getBoards(
            eq(BoardType.COMMUNITY),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any(Pageable.class),
            isNull()
        )).willReturn(boardPage);

        // when
        mockMvc.perform(get("/api/boards")
                .param("size", "20"))
            .andDo(print())
            .andExpect(status().isOk());

        // then
        ArgumentCaptor<Pageable> captor =
            ArgumentCaptor.forClass(Pageable.class);

        verify(boardService).getBoards(
            eq(BoardType.COMMUNITY),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            captor.capture(),
            isNull()
        );

        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("게시글 목록 조회 시 최대 페이지 크기는 100개로 제한한다")
    void getBoards_maxPageSize_test() throws Exception {
        // given
        PageImpl<BoardResponseDto> boardPage =
            new PageImpl<>(
                List.of(),
                PageRequest.of(0, 100),
                0
            );

        given(boardService.getBoards(
            eq(BoardType.COMMUNITY),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any(Pageable.class),
            isNull()
        )).willReturn(boardPage);

        // when
        mockMvc.perform(get("/api/boards")
                .param("size", "500"))
            .andDo(print())
            .andExpect(status().isOk());

        // then
        ArgumentCaptor<Pageable> captor =
            ArgumentCaptor.forClass(Pageable.class);

        verify(boardService).getBoards(
            eq(BoardType.COMMUNITY),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            captor.capture(),
            isNull()
        );

        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

}
