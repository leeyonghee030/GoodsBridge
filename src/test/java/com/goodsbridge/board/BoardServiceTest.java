
package com.goodsbridge.board;

import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private ReplyRepository replyRepository;

    @InjectMocks
    private BoardService boardService;

    private Board createBoard(Long authorId, Boolean isSecret) {
        Board board = Board.builder()
            .title("테스트 게시글")
            .content("테스트 내용")
            .authorId(authorId)
            .itemId(1L)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .isSecret(isSecret)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);
        return board;
    }

    private Reply createReply(Board board, Long authorId) {
        Reply reply = Reply.builder()
            .content("테스트 댓글")
            .authorId(authorId)
            .build();

        board.addReply(reply);
        ReflectionTestUtils.setField(reply, "id", 1L);
        return reply;
    }

    private ReplyRequestDto createReplyRequest(String content) {
        ReplyRequestDto dto = new ReplyRequestDto();
        ReflectionTestUtils.setField(dto, "content", content);
        return dto;
    }

    // =========================
    // 게시글 생성
    // =========================

    @Test
    @DisplayName("게시글을 생성하면 로그인 사용자가 작성자로 저장된다")
    void createBoard_test() {
        // given
        Long loginUserId = 10L;

        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("게시글 제목")
            .content("게시글 내용")
            .itemId(100L)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .isSecret(false)
            .build();

        Board savedBoard = createBoard(loginUserId, false);
        given(boardRepository.save(any(Board.class))).willReturn(savedBoard);

        // when
        Long boardId = boardService.createBoard(requestDto, loginUserId);

        // then
        ArgumentCaptor<Board> captor = ArgumentCaptor.forClass(Board.class);
        verify(boardRepository).save(captor.capture());

        Board saved = captor.getValue();

        assertThat(boardId).isEqualTo(1L);
        assertThat(saved.getTitle()).isEqualTo("게시글 제목");
        assertThat(saved.getContent()).isEqualTo("게시글 내용");
        assertThat(saved.getAuthorId()).isEqualTo(loginUserId);
        assertThat(saved.getItemId()).isEqualTo(100L);
        assertThat(saved.getBoardType())
            .isEqualTo(BoardType.PRODUCT_INQUIRY);
        assertThat(saved.getIsSecret()).isFalse();
        assertThat(saved.getStatus()).isEqualTo(BoardStatus.PENDING);
    }

    // =========================
    // 게시글 상세 조회
    // =========================

    @Test
    @DisplayName("일반 게시글은 비로그인 사용자도 조회할 수 있다")
    void getBoard_publicBoard_test() {
        // given
        Board board = createBoard(10L, false);

        given(boardRepository.findByIdWithReplies(1L))
            .willReturn(Optional.of(board));

        // when
        BoardResponseDto response = boardService.getBoard(1L, null);

        // then
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("테스트 게시글");
        assertThat(response.getContent()).isEqualTo("테스트 내용");
    }

    @Test
    @DisplayName("비밀글 작성자는 자신의 게시글을 조회할 수 있다")
    void getBoard_secretOwner_test() {
        // given
        Board board = createBoard(10L, true);

        given(boardRepository.findByIdWithReplies(1L))
            .willReturn(Optional.of(board));

        // when
        BoardResponseDto response = boardService.getBoard(1L, 10L);

        // then
        assertThat(response.getTitle()).isEqualTo("테스트 게시글");
    }

    @Test
    @DisplayName("비밀글은 작성자가 아닌 사용자가 조회할 수 없다")
    void getBoard_secretNonOwner_fail() {
        // given
        Board board = createBoard(10L, true);

        given(boardRepository.findByIdWithReplies(1L))
            .willReturn(Optional.of(board));

        // when & then
        assertThatThrownBy(() -> boardService.getBoard(1L, 20L))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("비밀글은 비로그인 사용자가 조회할 수 없다")
    void getBoard_secretGuest_fail() {
        // given
        Board board = createBoard(10L, true);

        given(boardRepository.findByIdWithReplies(1L))
            .willReturn(Optional.of(board));

        // when & then
        assertThatThrownBy(() -> boardService.getBoard(1L, null))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("존재하지 않는 게시글을 조회하면 예외가 발생한다")
    void getBoard_notFound_fail() {
        // given
        given(boardRepository.findByIdWithReplies(1L))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> boardService.getBoard(1L, 10L))
            .isInstanceOf(NotFoundException.class);
    }

    // =========================
    // 댓글 작성
    // =========================

    @Test
    @DisplayName("일반 게시글에는 로그인 사용자가 댓글을 작성할 수 있다")
    void createReply_publicBoard_test() {
        // given
        Board board = createBoard(10L, false);
        ReplyRequestDto requestDto = createReplyRequest("댓글 내용");

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        given(replyRepository.save(any(Reply.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        boardService.createReply(1L, requestDto, 20L);

        // then
        ArgumentCaptor<Reply> captor = ArgumentCaptor.forClass(Reply.class);
        verify(replyRepository).save(captor.capture());

        Reply savedReply = captor.getValue();

        assertThat(savedReply.getContent()).isEqualTo("댓글 내용");
        assertThat(savedReply.getAuthorId()).isEqualTo(20L);
        assertThat(savedReply.getBoard()).isSameAs(board);
        assertThat(board.getReplies()).contains(savedReply);
        assertThat(board.getStatus()).isEqualTo(BoardStatus.COMPLETED);
    }

    @Test
    @DisplayName("비밀글 작성자는 자신의 게시글에 댓글을 작성할 수 있다")
    void createReply_secretOwner_test() {
        // given
        Board board = createBoard(10L, true);
        ReplyRequestDto requestDto = createReplyRequest("작성자 댓글");

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        given(replyRepository.save(any(Reply.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        boardService.createReply(1L, requestDto, 10L);

        // then
        assertThat(board.getReplies()).hasSize(1);
        assertThat(board.getStatus()).isEqualTo(BoardStatus.COMPLETED);
    }

    @Test
    @DisplayName("비밀글은 작성자가 아닌 사용자가 댓글을 작성할 수 없다")
    void createReply_secretNonOwner_fail() {
        // given
        Board board = createBoard(10L, true);
        ReplyRequestDto requestDto = createReplyRequest("댓글 내용");

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        // when & then
        assertThatThrownBy(() ->
            boardService.createReply(1L, requestDto, 20L))
            .isInstanceOf(ForbiddenException.class);

        verify(replyRepository, never()).save(any(Reply.class));
        assertThat(board.getReplies()).isEmpty();
    }

    @Test
    @DisplayName("비밀글은 비로그인 사용자가 댓글을 작성할 수 없다")
    void createReply_secretGuest_fail() {
        // given
        Board board = createBoard(10L, true);
        ReplyRequestDto requestDto = createReplyRequest("댓글 내용");

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        // when & then
        assertThatThrownBy(() ->
            boardService.createReply(1L, requestDto, null))
            .isInstanceOf(ForbiddenException.class);

        verify(replyRepository, never()).save(any(Reply.class));
    }

    @Test
    @DisplayName("존재하지 않는 게시글에 댓글을 작성하면 예외가 발생한다")
    void createReply_boardNotFound_fail() {
        // given
        ReplyRequestDto requestDto = createReplyRequest("댓글 내용");

        given(boardRepository.findById(1L))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
            boardService.createReply(1L, requestDto, 10L))
            .isInstanceOf(NotFoundException.class);

        verify(replyRepository, never()).save(any(Reply.class));
    }

    // =========================
    // 댓글 삭제
    // =========================

    @Test
    @DisplayName("댓글 작성자는 자신의 댓글을 삭제할 수 있으며 다른 댓글이 남아 있으면 게시글 상태를 유지한다")
    void deleteReply_test() {
        // given
        Board board = createBoard(10L, false);

        Reply reply1 = createReply(board, 20L);

        Reply reply2 = Reply.builder()
            .content("두 번째 댓글")
            .authorId(30L)
            .build();

        board.addReply(reply2);
        ReflectionTestUtils.setField(reply2, "id", 2L);

        board.complete();

        given(replyRepository.findById(1L))
            .willReturn(Optional.of(reply1));

        given(replyRepository.countByBoardId(1L))
            .willReturn(1L);

        // when
        boardService.deleteReply(1L, 20L);

        // then
        assertThat(board.getReplies())
            .doesNotContain(reply1)
            .contains(reply2)
            .hasSize(1);

        assertThat(board.getStatus())
            .isEqualTo(BoardStatus.COMPLETED);

        verify(replyRepository).countByBoardId(1L);
    }

    @Test
    @DisplayName("마지막 댓글을 삭제하면 게시글 상태가 답변 대기로 변경된다")
    void deleteReply_lastReply_test() {
        // given
        Board board = createBoard(10L, false);
        Reply reply = createReply(board, 20L);
        board.complete();

        given(replyRepository.findById(1L))
            .willReturn(Optional.of(reply));

        given(replyRepository.countByBoardId(1L))
            .willReturn(0L);

        // when
        boardService.deleteReply(1L, 20L);

        // then
        assertThat(board.getReplies()).isEmpty();
        assertThat(board.getStatus()).isEqualTo(BoardStatus.PENDING);
    }

    @Test
    @DisplayName("댓글 작성자가 아닌 사용자는 댓글을 삭제할 수 없다")
    void deleteReply_nonAuthor_fail() {
        // given
        Board board = createBoard(10L, false);
        Reply reply = createReply(board, 20L);

        given(replyRepository.findById(1L))
            .willReturn(Optional.of(reply));

        // when & then
        assertThatThrownBy(() ->
            boardService.deleteReply(1L, 30L))
            .isInstanceOf(ForbiddenException.class);

        assertThat(board.getReplies()).contains(reply);
        verify(replyRepository, never()).countByBoardId(any());
    }

    @Test
    @DisplayName("존재하지 않는 댓글을 삭제하면 예외가 발생한다")
    void deleteReply_notFound_fail() {
        // given
        given(replyRepository.findById(1L))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
            boardService.deleteReply(1L, 20L))
            .isInstanceOf(NotFoundException.class);
    }

    // =========================
    // 게시글 수정
    // =========================

    @Test
    @DisplayName("게시글 작성자는 자신의 게시글을 수정할 수 있다")
    void updateBoard_test() {
        // given
        Board board = createBoard(10L, false);

        BoardUpdateRequestDto requestDto = BoardUpdateRequestDto.builder()
            .title("수정된 제목")
            .content("수정된 내용")
            .isSecret(true)
            .build();

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        // when
        boardService.updateBoard(1L, requestDto, 10L);

        // then
        assertThat(board.getTitle()).isEqualTo("수정된 제목");
        assertThat(board.getContent()).isEqualTo("수정된 내용");
        assertThat(board.getIsSecret()).isTrue();

        // 상품 ID와 게시판 유형은 변경되지 않아야 한다.
        assertThat(board.getItemId()).isEqualTo(1L);
        assertThat(board.getBoardType())
            .isEqualTo(BoardType.PRODUCT_INQUIRY);
    }

    @Test
    @DisplayName("게시글 작성자가 아닌 사용자는 게시글을 수정할 수 없다")
    void updateBoard_nonAuthor_fail() {
        // given
        Board board = createBoard(10L, false);

        BoardUpdateRequestDto requestDto = BoardUpdateRequestDto.builder()
            .title("수정된 제목")
            .content("수정된 내용")
            .isSecret(false)
            .build();

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        // when & then
        assertThatThrownBy(() ->
            boardService.updateBoard(1L, requestDto, 20L))
            .isInstanceOf(ForbiddenException.class);

        assertThat(board.getTitle()).isEqualTo("테스트 게시글");
        assertThat(board.getContent()).isEqualTo("테스트 내용");
        assertThat(board.getItemId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 게시글을 수정하면 예외가 발생한다")
    void updateBoard_notFound_fail() {
        // given
        BoardUpdateRequestDto requestDto = BoardUpdateRequestDto.builder()
            .title("수정된 제목")
            .content("수정된 내용")
            .build();

        given(boardRepository.findById(1L))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
            boardService.updateBoard(1L, requestDto, 10L))
            .isInstanceOf(NotFoundException.class);
    }

    // =========================
    // 게시글 삭제
    // =========================

    @Test
    @DisplayName("게시글 작성자는 자신의 게시글을 삭제할 수 있다")
    void deleteBoard_test() {
        // given
        Board board = createBoard(10L, false);

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        // when
        boardService.deleteBoard(1L, 10L);

        // then
        verify(boardRepository).delete(board);
    }

    @Test
    @DisplayName("게시글 작성자가 아닌 사용자는 게시글을 삭제할 수 없다")
    void deleteBoard_nonAuthor_fail() {
        // given
        Board board = createBoard(10L, false);

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        // when & then
        assertThatThrownBy(() ->
            boardService.deleteBoard(1L, 20L))
            .isInstanceOf(ForbiddenException.class);

        verify(boardRepository, never()).delete(any(Board.class));
    }

    @Test
    @DisplayName("존재하지 않는 게시글을 삭제하면 예외가 발생한다")
    void deleteBoard_notFound_fail() {
        // given
        given(boardRepository.findById(1L))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
            boardService.deleteBoard(1L, 10L))
            .isInstanceOf(NotFoundException.class);

        verify(boardRepository, never()).delete(any(Board.class));
    }

    // =========================
    // 게시글 목록 및 검색
    // =========================

    @Test
    @DisplayName("검색어가 없으면 전체 게시글을 페이징 조회한다")
    void getBoards_all_test() {
        // given
        Board board = createBoard(10L, false);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> boardPage = new PageImpl<>(List.of(board));

        given(boardRepository.findAll(pageable))
            .willReturn(boardPage);

        // when
        Page<BoardResponseDto> result =
            boardService.getBoards(null, pageable, null);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle())
            .isEqualTo("테스트 게시글");

        verify(boardRepository).findAll(pageable);
        verify(boardRepository, never())
            .findByTitleContainingOrContentContaining(
                any(String.class), any(String.class), any(Pageable.class));
    }

    @Test
    @DisplayName("검색어가 있으면 제목 또는 내용으로 검색한다")
    void getBoards_search_test() {
        // given
        Board board = createBoard(10L, false);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> boardPage = new PageImpl<>(List.of(board));

        given(boardRepository.findByTitleContainingOrContentContaining(
            eq("공지"), eq("공지"), eq(pageable)))
            .willReturn(boardPage);

        // when
        Page<BoardResponseDto> result =
            boardService.getBoards("공지", pageable, null);

        // then
        assertThat(result.getContent()).hasSize(1);

        verify(boardRepository)
            .findByTitleContainingOrContentContaining(
                "공지", "공지", pageable);
    }

    @Test
    @DisplayName("검색어 앞뒤 공백을 제거한 후 검색한다")
    void getBoards_trimKeyword_test() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> boardPage = new PageImpl<>(List.of());

        given(boardRepository.findByTitleContainingOrContentContaining(
            eq("공지"), eq("공지"), eq(pageable)))
            .willReturn(boardPage);

        // when
        boardService.getBoards("  공지  ", pageable, null);

        // then
        verify(boardRepository)
            .findByTitleContainingOrContentContaining(
                "공지", "공지", pageable);
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 비밀글 목록의 내용을 볼 수 없다")
    void getBoards_secretMasking_guest_test() {
        // given
        Board board = createBoard(10L, true);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> boardPage = new PageImpl<>(List.of(board));

        given(boardRepository.findAll(pageable))
            .willReturn(boardPage);

        // when
        Page<BoardResponseDto> result =
            boardService.getBoards(null, pageable, null);

        // then
        BoardResponseDto response = result.getContent().get(0);

        assertThat(response.getTitle()).isEqualTo("비밀글입니다.");
        assertThat(response.getContent()).isNull();
        assertThat(response.getAuthorId()).isNull();
        assertThat(response.getItemId()).isNull();
        assertThat(response.getReplies()).isEmpty();
    }

    @Test
    @DisplayName("비밀글 작성자는 목록에서 자신의 게시글 내용을 확인할 수 있다")
    void getBoards_secretOwner_test() {
        // given
        Board board = createBoard(10L, true);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> boardPage = new PageImpl<>(List.of(board));

        given(boardRepository.findAll(pageable))
            .willReturn(boardPage);

        // when
        Page<BoardResponseDto> result =
            boardService.getBoards(null, pageable, 10L);

        // then
        BoardResponseDto response = result.getContent().get(0);

        assertThat(response.getTitle()).isEqualTo("테스트 게시글");
        assertThat(response.getContent()).isEqualTo("테스트 내용");
        assertThat(response.getAuthorId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("특정 상품의 문의 게시글만 페이징 조회한다")
    void getProductInquiries_test() {
        // given
        Long itemId = 100L;
        Long loginUserId = 10L;

        Board board = createBoard(10L, false);
        ReflectionTestUtils.setField(board, "itemId", itemId);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> boardPage = new PageImpl<>(List.of(board));

        given(boardRepository.findByItemIdAndBoardType(
            itemId,
            BoardType.PRODUCT_INQUIRY,
            pageable
        )).willReturn(boardPage);

        // when
        Page<BoardResponseDto> result =
            boardService.getProductInquiries(itemId, pageable, loginUserId);

        // then
        assertThat(result.getContent()).hasSize(1);

        BoardResponseDto response = result.getContent().get(0);

        assertThat(response.getTitle()).isEqualTo("테스트 게시글");
        assertThat(response.getItemId()).isEqualTo(itemId);

        verify(boardRepository).findByItemIdAndBoardType(
            itemId,
            BoardType.PRODUCT_INQUIRY,
            pageable
        );
    }

    @Test
    @DisplayName("상품 문의 게시글이 없으면 빈 페이지를 반환한다")
    void getProductInquiries_empty_test() {
        // given
        Long itemId = 100L;

        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> emptyPage = new PageImpl<>(List.of());

        given(boardRepository.findByItemIdAndBoardType(
            itemId,
            BoardType.PRODUCT_INQUIRY,
            pageable
        )).willReturn(emptyPage);

        // when
        Page<BoardResponseDto> result =
            boardService.getProductInquiries(itemId, pageable, null);

        // then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();

        verify(boardRepository).findByItemIdAndBoardType(
            itemId,
            BoardType.PRODUCT_INQUIRY,
            pageable
        );
    }
}
