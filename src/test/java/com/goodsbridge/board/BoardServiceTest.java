
package com.goodsbridge.board;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.purchase.PurchaseRepository;
import com.goodsbridge.user.UserRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private ReplyRepository replyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private BoardService boardService;

    private Board createBoard(Long authorId, Boolean isSecret) {
        Board board = Board.builder()
            .title("테스트 게시글")
            .content("테스트 내용")
            .authorId(authorId)
            .itemId(null)
            .boardType(BoardType.COMMUNITY)
            .isSecret(isSecret)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);
        return board;
    }

    private Board createSecretInquiryBoard(Long authorId) {
        Board board = Board.builder()
            .title("비밀 상품 문의")
            .content("비밀 문의 내용")
            .authorId(authorId)
            .itemId(100L)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .isSecret(true)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);
        return board;
    }

    private Board createLegacyBoard(Long authorId) {
        Board board = Board.builder()
            .title("기존 게시글")
            .content("기존 게시글 내용")
            .authorId(authorId)
            .itemId(null)
            .boardType(BoardType.LEGACY)
            .isSecret(false)
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
            .boardType(BoardType.COMMUNITY)
            .isSecret(false)
            .build();

        Board savedBoard = createBoard(loginUserId, false);

        given(boardRepository.save(any(Board.class)))
            .willReturn(savedBoard);

        // when
        Long boardId = boardService.createBoard(requestDto, loginUserId);

        // then
        ArgumentCaptor<Board> captor =
            ArgumentCaptor.forClass(Board.class);

        verify(boardRepository).save(captor.capture());

        Board saved = captor.getValue();

        assertThat(boardId).isEqualTo(1L);
        assertThat(saved.getTitle()).isEqualTo("게시글 제목");
        assertThat(saved.getContent()).isEqualTo("게시글 내용");
        assertThat(saved.getAuthorId()).isEqualTo(loginUserId);
        assertThat(saved.getBoardType()).isEqualTo(BoardType.COMMUNITY);
        assertThat(saved.getIsSecret()).isFalse();
        assertThat(saved.getStatus()).isEqualTo(BoardStatus.PENDING);
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 게시글을 생성할 수 없다")
    void createBoard_guest_fail() {
        // given
        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("게시글 제목")
            .content("게시글 내용")
            .boardType(BoardType.COMMUNITY)
            .build();

        // when & then
        assertThatThrownBy(() ->
            boardService.createBoard(requestDto, null))
            .isInstanceOf(ForbiddenException.class);

        verify(boardRepository, never()).save(any(Board.class));
    }

    @Test
    @DisplayName("기존 게시글 유형인 LEGACY로 신규 게시글을 생성할 수 없다")
    void createBoard_legacy_fail() {
        // given
        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("게시글 제목")
            .content("게시글 내용")
            .boardType(BoardType.LEGACY)
            .build();

        // when & then
        assertThatThrownBy(() ->
            boardService.createBoard(requestDto, 10L))
            .isInstanceOf(BadRequestException.class);

        verify(boardRepository, never()).save(any(Board.class));
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

        given(userRepository.findAllById(any()))
            .willReturn(List.of());

        // when
        BoardResponseDto response = boardService.getBoard(1L, null);

        // then
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("테스트 게시글");
        assertThat(response.getContent()).isEqualTo("테스트 내용");
    }

    @Test
    @DisplayName("비밀 상품 문의 작성자는 자신의 게시글을 조회할 수 있다")
    void getBoard_secretOwner_test() {
        // given
        Board board = createSecretInquiryBoard(10L);

        given(boardRepository.findByIdWithReplies(1L))
            .willReturn(Optional.of(board));

        given(itemRepository.findById(100L))
            .willReturn(Optional.empty());

        given(userRepository.findAllById(any()))
            .willReturn(List.of());

        // when
        BoardResponseDto response = boardService.getBoard(1L, 10L);

        // then
        assertThat(response.getTitle()).isEqualTo("비밀 상품 문의");
        assertThat(response.getContent()).isEqualTo("비밀 문의 내용");
    }

    @Test
    @DisplayName("비밀 상품 문의는 작성자가 아닌 사용자가 조회할 수 없다")
    void getBoard_secretNonOwner_fail() {
        // given
        Board board = createSecretInquiryBoard(10L);

        given(boardRepository.findByIdWithReplies(1L))
            .willReturn(Optional.of(board));

        given(itemRepository.findById(100L))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> boardService.getBoard(1L, 20L))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("비밀 상품 문의는 비로그인 사용자가 조회할 수 없다")
    void getBoard_secretGuest_fail() {
        // given
        Board board = createSecretInquiryBoard(10L);

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

    @Test
    @DisplayName("LEGACY 게시글은 상세 조회할 수 없다")
    void getBoard_legacy_fail() {
        // given
        Board board = createLegacyBoard(10L);

        given(boardRepository.findByIdWithReplies(1L))
            .willReturn(Optional.of(board));

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
        ArgumentCaptor<Reply> captor =
            ArgumentCaptor.forClass(Reply.class);

        verify(replyRepository).save(captor.capture());

        Reply savedReply = captor.getValue();

        assertThat(savedReply.getContent()).isEqualTo("댓글 내용");
        assertThat(savedReply.getAuthorId()).isEqualTo(20L);
        assertThat(savedReply.getBoard()).isSameAs(board);
        assertThat(board.getReplies()).contains(savedReply);
    }

    @Test
    @DisplayName("비밀 상품 문의 작성자는 자신의 게시글에 댓글을 작성할 수 있다")
    void createReply_secretOwner_test() {
        // given
        Board board = createSecretInquiryBoard(10L);
        ReplyRequestDto requestDto = createReplyRequest("작성자 댓글");

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        given(replyRepository.save(any(Reply.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        boardService.createReply(1L, requestDto, 10L);

        // then
        assertThat(board.getReplies()).hasSize(1);
        verify(replyRepository).save(any(Reply.class));
    }

    @Test
    @DisplayName("비밀 상품 문의는 작성자와 판매자가 아닌 사용자가 댓글을 작성할 수 없다")
    void createReply_secretNonOwner_fail() {
        // given
        Board board = createSecretInquiryBoard(10L);
        ReplyRequestDto requestDto = createReplyRequest("댓글 내용");

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        given(itemRepository.findById(100L))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
            boardService.createReply(1L, requestDto, 20L))
            .isInstanceOf(ForbiddenException.class);

        verify(replyRepository, never()).save(any(Reply.class));
        assertThat(board.getReplies()).isEmpty();
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

    @Test
    @DisplayName("LEGACY 게시글에는 댓글을 작성할 수 없다")
    void createReply_legacy_fail() {
        // given
        Board board = createLegacyBoard(10L);
        ReplyRequestDto requestDto = createReplyRequest("댓글 내용");

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

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
    @DisplayName("댓글 작성자는 자신의 댓글을 삭제할 수 있다")
    void deleteReply_test() {
        // given
        Board board = createBoard(10L, false);
        Reply reply = createReply(board, 20L);

        given(replyRepository.findById(1L))
            .willReturn(Optional.of(reply));

        given(replyRepository.countByBoardId(board.getId()))
            .willReturn(1L);

        given(replyRepository.deleteByIdInBulk(1L))
            .willReturn(1);

        // when
        boardService.deleteReply(1L, 20L);

        // then
        verify(replyRepository).countByBoardId(board.getId());
        verify(replyRepository).deleteByIdInBulk(1L);
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
        verify(replyRepository, never()).delete(any(Reply.class));
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

    @Test
    @DisplayName("LEGACY 게시글의 댓글은 삭제할 수 없다")
    void deleteReply_legacy_fail() {
        // given
        Board board = createLegacyBoard(10L);
        Reply reply = createReply(board, 20L);

        given(replyRepository.findById(1L))
            .willReturn(Optional.of(reply));

        // when & then
        assertThatThrownBy(() ->
            boardService.deleteReply(1L, 20L))
            .isInstanceOf(NotFoundException.class);

        verify(replyRepository, never()).delete(any(Reply.class));
    }

    // =========================
    // 게시글 수정
    // =========================

    @Test
    @DisplayName("커뮤니티 게시글 수정 시 비밀글 설정을 해제한다")
    void updateBoard_test() {
        // given
        Board board = createBoard(10L, false);

        BoardUpdateRequestDto requestDto =
            BoardUpdateRequestDto.builder()
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
        assertThat(board.getIsSecret()).isFalse();

        // 상품 ID와 게시판 유형은 변경되지 않아야 한다.
        assertThat(board.getItemId()).isNull();
        assertThat(board.getBoardType()).isEqualTo(BoardType.COMMUNITY);
    }

    @Test
    @DisplayName("게시글 작성자가 아닌 사용자는 게시글을 수정할 수 없다")
    void updateBoard_nonAuthor_fail() {
        // given
        Board board = createBoard(10L, false);

        BoardUpdateRequestDto requestDto =
            BoardUpdateRequestDto.builder()
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
    }

    @Test
    @DisplayName("존재하지 않는 게시글을 수정하면 예외가 발생한다")
    void updateBoard_notFound_fail() {
        // given
        BoardUpdateRequestDto requestDto =
            BoardUpdateRequestDto.builder()
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

    @Test
    @DisplayName("LEGACY 게시글은 수정할 수 없다")
    void updateBoard_legacy_fail() {
        // given
        Board board = createLegacyBoard(10L);

        BoardUpdateRequestDto requestDto =
            BoardUpdateRequestDto.builder()
                .title("수정된 제목")
                .content("수정된 내용")
                .build();

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

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

    @Test
    @DisplayName("LEGACY 게시글은 삭제할 수 없다")
    void deleteBoard_legacy_fail() {
        // given
        Board board = createLegacyBoard(10L);

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

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
    @DisplayName("검색어가 없으면 해당 게시판 유형의 게시글을 페이징 조회한다")
    void getBoards_all_test() {
        // given
        Board board = createBoard(10L, false);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> boardPage =
            new PageImpl<>(List.of(board));

        given(boardRepository.findByBoardType(
            BoardType.COMMUNITY,
            pageable
        )).willReturn(boardPage);

        given(userRepository.findAllById(any()))
            .willReturn(List.of());

        // when
        Page<BoardResponseDto> result =
            boardService.getBoards(
                BoardType.COMMUNITY,
                null,
                pageable,
                null
            );

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle())
            .isEqualTo("테스트 게시글");

        verify(boardRepository).findByBoardType(
            BoardType.COMMUNITY,
            pageable
        );
    }

    @Test
    @DisplayName("검색어가 있으면 제목, 내용, 작성자 닉네임으로 검색한다")
    void getBoards_search_test() {
        // given
        Board board = createBoard(10L, false);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> boardPage =
            new PageImpl<>(List.of(board));

        given(boardRepository.findByKeyword(
            eq("공지"),
            eq(List.of(BoardType.COMMUNITY)),
            eq(pageable)
        )).willReturn(boardPage);

        given(userRepository.findAllById(any()))
            .willReturn(List.of());

        // when
        Page<BoardResponseDto> result =
            boardService.getBoards(
                BoardType.COMMUNITY,
                "공지",
                pageable,
                null
            );

        // then
        assertThat(result.getContent()).hasSize(1);

        verify(boardRepository).findByKeyword(
            "공지",
            List.of(BoardType.COMMUNITY),
            pageable
        );
    }

    @Test
    @DisplayName("검색어 앞뒤 공백을 제거한 후 검색한다")
    void getBoards_trimKeyword_test() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> emptyPage =
            new PageImpl<>(List.of());

        given(boardRepository.findByKeyword(
            eq("공지"),
            eq(List.of(BoardType.COMMUNITY)),
            eq(pageable)
        )).willReturn(emptyPage);

        // when
        boardService.getBoards(
            BoardType.COMMUNITY,
            "  공지  ",
            pageable,
            null
        );

        // then
        verify(boardRepository).findByKeyword(
            "공지",
            List.of(BoardType.COMMUNITY),
            pageable
        );
    }

    @Test
    @DisplayName("비로그인 사용자는 비밀 상품 문의 목록의 내용을 볼 수 없다")
    void getBoards_secretMasking_guest_test() {
        // given
        Board board = createSecretInquiryBoard(10L);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> boardPage =
            new PageImpl<>(List.of(board));

        given(boardRepository.findByBoardType(
            BoardType.PRODUCT_INQUIRY,
            pageable
        )).willReturn(boardPage);

        given(itemRepository.findAllById(any()))
            .willReturn(List.of());

        given(userRepository.findAllById(any()))
            .willReturn(List.of());

        // when
        Page<BoardResponseDto> result =
            boardService.getBoards(
                BoardType.PRODUCT_INQUIRY,
                null,
                pageable,
                null
            );

        // then
        BoardResponseDto response = result.getContent().get(0);

        assertThat(response.getTitle()).isEqualTo("비밀글입니다.");
        assertThat(response.getContent()).isNull();
        assertThat(response.getAuthorId()).isNull();
        assertThat(response.getItemId()).isNull();
        assertThat(response.getReplies()).isEmpty();
    }

    @Test
    @DisplayName("비밀 상품 문의 작성자는 목록에서 자신의 게시글 내용을 확인할 수 있다")
    void getBoards_secretOwner_test() {
        // given
        Board board = createSecretInquiryBoard(10L);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> boardPage =
            new PageImpl<>(List.of(board));

        given(boardRepository.findByBoardType(
            BoardType.PRODUCT_INQUIRY,
            pageable
        )).willReturn(boardPage);

        given(itemRepository.findAllById(any()))
            .willReturn(List.of());

        given(userRepository.findAllById(any()))
            .willReturn(List.of());

        // when
        Page<BoardResponseDto> result =
            boardService.getBoards(
                BoardType.PRODUCT_INQUIRY,
                null,
                pageable,
                10L
            );

        // then
        BoardResponseDto response = result.getContent().get(0);

        assertThat(response.getTitle()).isEqualTo("비밀 상품 문의");
        assertThat(response.getContent()).isEqualTo("비밀 문의 내용");
        assertThat(response.getAuthorId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("LEGACY 유형의 게시글 목록은 조회할 수 없다")
    void getBoards_legacy_fail() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        // when & then
        assertThatThrownBy(() ->
            boardService.getBoards(
                BoardType.LEGACY,
                null,
                pageable,
                10L
            ))
            .isInstanceOf(BadRequestException.class);

        verify(boardRepository, never())
            .findByBoardType(any(), any(Pageable.class));
    }

    @Test
    @DisplayName("상품 문의를 생성하면 상품 ID와 비밀글 설정이 저장된다")
    void createBoard_productInquiry_test() {
        // given
        Long loginUserId = 10L;
        Long itemId = 100L;

        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("상품 문의")
            .content("상품에 대해 문의합니다.")
            .boardType(BoardType.PRODUCT_INQUIRY)
            .itemId(itemId)
            .isSecret(true)
            .build();

        given(itemRepository.findById(itemId))
            .willReturn(Optional.of(
                mock(com.goodsbridge.item.Item.class)
            ));

        given(boardRepository.save(any(Board.class)))
            .willAnswer(invocation -> {
                Board board = invocation.getArgument(0);
                ReflectionTestUtils.setField(board, "id", 1L);
                return board;
            });

        // when
        Long boardId = boardService.createBoard(requestDto, loginUserId);

        // then
        ArgumentCaptor<Board> captor =
            ArgumentCaptor.forClass(Board.class);

        verify(boardRepository).save(captor.capture());

        Board saved = captor.getValue();

        assertThat(boardId).isEqualTo(1L);
        assertThat(saved.getAuthorId()).isEqualTo(loginUserId);
        assertThat(saved.getItemId()).isEqualTo(itemId);
        assertThat(saved.getBoardType()).isEqualTo(BoardType.PRODUCT_INQUIRY);
        assertThat(saved.getIsSecret()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(BoardStatus.PENDING);
    }


    @Test
    @DisplayName("상품 문의 생성 시 상품 ID가 없으면 예외가 발생한다")
    void createBoard_productInquiry_itemIdNull_fail() {
        // given
        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("상품 문의")
            .content("상품 문의 내용")
            .boardType(BoardType.PRODUCT_INQUIRY)
            .build();

        // when & then
        assertThatThrownBy(() ->
            boardService.createBoard(requestDto, 10L))
            .isInstanceOf(BadRequestException.class);

        verify(itemRepository, never()).findById(any());
        verify(boardRepository, never()).save(any(Board.class));
    }

    @Test
    @DisplayName("존재하지 않는 상품에 문의를 작성하면 예외가 발생한다")
    void createBoard_productInquiry_itemNotFound_fail() {
        // given
        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("상품 문의")
            .content("상품 문의 내용")
            .boardType(BoardType.PRODUCT_INQUIRY)
            .itemId(100L)
            .build();

        given(itemRepository.findById(100L))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
            boardService.createBoard(requestDto, 10L))
            .isInstanceOf(NotFoundException.class);

        verify(boardRepository, never()).save(any(Board.class));
    }

    @Test
    @DisplayName("구매한 상품에 대해서는 리뷰를 작성할 수 있다")
    void createBoard_productReview_test() {
        // given
        Long loginUserId = 10L;
        Long itemId = 100L;

        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("상품 리뷰")
            .content("상품 구매 후기입니다.")
            .boardType(BoardType.PRODUCT_REVIEW)
            .itemId(itemId)
            .isSecret(true)
            .build();

        Item item = mock(Item.class);

        given(itemRepository.findById(itemId))
            .willReturn(Optional.of(item));

        given(purchaseRepository.existsByUserIdAndItemId(loginUserId, itemId))
            .willReturn(true);

        given(boardRepository.save(any(Board.class)))
            .willAnswer(invocation -> {
                Board board = invocation.getArgument(0);
                ReflectionTestUtils.setField(board, "id", 1L);
                return board;
            });

        // when
        Long boardId = boardService.createBoard(requestDto, loginUserId);

        // then
        ArgumentCaptor<Board> captor =
            ArgumentCaptor.forClass(Board.class);

        verify(boardRepository).save(captor.capture());

        Board saved = captor.getValue();

        assertThat(boardId).isEqualTo(1L);
        assertThat(saved.getAuthorId()).isEqualTo(loginUserId);
        assertThat(saved.getItemId()).isEqualTo(itemId);
        assertThat(saved.getBoardType()).isEqualTo(BoardType.PRODUCT_REVIEW);
        assertThat(saved.getIsSecret()).isFalse();
        assertThat(saved.getStatus()).isEqualTo(BoardStatus.PENDING);
    }

    @Test
    @DisplayName("상품 리뷰 생성 시 상품 ID가 없으면 예외가 발생한다")
    void createBoard_productReview_itemIdNull_fail() {
        // given
        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("상품 리뷰")
            .content("상품 구매 후기입니다.")
            .boardType(BoardType.PRODUCT_REVIEW)
            .build();

        // when & then
        assertThatThrownBy(() ->
            boardService.createBoard(requestDto, 10L))
            .isInstanceOf(BadRequestException.class);

        verify(purchaseRepository, never())
            .existsByUserIdAndItemId(any(), any());

        verify(boardRepository, never()).save(any(Board.class));
    }

    @Test
    @DisplayName("구매하지 않은 상품은 리뷰를 작성할 수 없다")
    void createBoard_productReview_notPurchased_fail() {
        // given
        Long loginUserId = 10L;
        Long itemId = 100L;

        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("상품 리뷰")
            .content("상품 구매 후기입니다.")
            .boardType(BoardType.PRODUCT_REVIEW)
            .itemId(itemId)
            .build();

        Item item = mock(Item.class);

        given(itemRepository.findById(itemId))
            .willReturn(Optional.of(item));

        given(purchaseRepository.existsByUserIdAndItemId(loginUserId, itemId))
            .willReturn(false);

        // when & then
        assertThatThrownBy(() ->
            boardService.createBoard(requestDto, loginUserId))
            .isInstanceOf(ForbiddenException.class);

        verify(boardRepository, never()).save(any(Board.class));
    }

    @Test
    @DisplayName("고객센터 게시글을 정상적으로 생성할 수 있다")
    void createBoard_customerService_test() {
        // given
        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("고객센터 문의")
            .content("서비스 이용 문의입니다.")
            .boardType(BoardType.CUSTOMER_SERVICE)
            .isSecret(true)
            .build();

        given(boardRepository.save(any(Board.class)))
            .willAnswer(invocation -> {
                Board board = invocation.getArgument(0);
                ReflectionTestUtils.setField(board, "id", 1L);
                return board;
            });

        // when
        Long boardId = boardService.createBoard(requestDto, 10L);

        // then
        ArgumentCaptor<Board> captor =
            ArgumentCaptor.forClass(Board.class);

        verify(boardRepository).save(captor.capture());

        Board saved = captor.getValue();

        assertThat(boardId).isEqualTo(1L);
        assertThat(saved.getAuthorId()).isEqualTo(10L);
        assertThat(saved.getBoardType()).isEqualTo(BoardType.CUSTOMER_SERVICE);
        assertThat(saved.getIsSecret()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(BoardStatus.PENDING);
    }

    @Test
    @DisplayName("커뮤니티 게시글은 비밀글로 생성할 수 없다")
    void createBoard_communitySecret_test() {
        // given
        BoardRequestDto requestDto = BoardRequestDto.builder()
            .title("커뮤니티 게시글")
            .content("커뮤니티 게시글 내용")
            .boardType(BoardType.COMMUNITY)
            .isSecret(true)
            .build();

        given(boardRepository.save(any(Board.class)))
            .willAnswer(invocation -> {
                Board board = invocation.getArgument(0);
                ReflectionTestUtils.setField(board, "id", 1L);
                return board;
            });

        // when
        Long boardId = boardService.createBoard(requestDto, 10L);

        // then
        ArgumentCaptor<Board> captor =
            ArgumentCaptor.forClass(Board.class);

        verify(boardRepository).save(captor.capture());

        Board saved = captor.getValue();

        assertThat(boardId).isEqualTo(1L);
        assertThat(saved.getBoardType()).isEqualTo(BoardType.COMMUNITY);
        assertThat(saved.getIsSecret()).isFalse();
    }

    @Test
    @DisplayName("상품 문의에 판매자가 댓글을 작성하면 게시글 상태가 COMPLETED로 변경된다")
    void createReply_productInquiry_seller_test() {
        // given
        Long authorId = 10L;
        Long sellerId = 20L;
        Long itemId = 100L;

        Board board = Board.builder()
            .title("상품 문의")
            .content("상품 문의 내용")
            .authorId(authorId)
            .itemId(itemId)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .isSecret(true)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);

        Item item = mock(Item.class);
        given(item.getSellerUserId()).willReturn(sellerId);
        given(itemRepository.findById(itemId))
            .willReturn(Optional.of(item));

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        given(replyRepository.save(any(Reply.class)))
            .willAnswer(invocation -> {
                Reply reply = invocation.getArgument(0);
                ReflectionTestUtils.setField(reply, "id", 1L);
                return reply;
            });

        // when
        Long replyId = boardService.createReply(
            1L,
            createReplyRequest("판매자 답변입니다."),
            sellerId
        );

        // then
        assertThat(replyId).isEqualTo(1L);
        assertThat(board.getReplies()).hasSize(1);
        assertThat(board.getReplies().get(0).getAuthorId()).isEqualTo(sellerId);
        assertThat(board.getStatus()).isEqualTo(BoardStatus.COMPLETED);

        verify(replyRepository).save(any(Reply.class));
    }

    @Test
    @DisplayName("상품 문의 작성자가 추가 댓글을 작성하면 게시글 상태가 PENDING으로 변경된다")
    void createReply_productInquiry_author_pending_test() {
        // given
        Long authorId = 10L;
        Long itemId = 100L;

        Board board = Board.builder()
            .title("상품 문의")
            .content("추가 질문")
            .authorId(authorId)
            .itemId(itemId)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .isSecret(true)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);

        board.complete();

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        given(itemRepository.findById(itemId))
            .willReturn(Optional.empty());

        given(replyRepository.save(any(Reply.class)))
            .willAnswer(invocation -> {
                Reply reply = invocation.getArgument(0);
                ReflectionTestUtils.setField(reply, "id", 1L);
                return reply;
            });

        // when
        boardService.createReply(
            1L,
            createReplyRequest("추가 질문입니다."),
            authorId
        );

        // then
        assertThat(board.getStatus()).isEqualTo(BoardStatus.PENDING);
        assertThat(board.getReplies()).hasSize(1);

        verify(replyRepository).save(any(Reply.class));
    }

    @Test
    @DisplayName("고객센터 작성자가 댓글을 작성하면 게시글 상태가 PENDING으로 변경된다")
    void createReply_customerService_author_pending_test() {
        // given
        Long authorId = 10L;

        Board board = Board.builder()
            .title("고객센터 문의")
            .content("추가 문의")
            .authorId(authorId)
            .boardType(BoardType.CUSTOMER_SERVICE)
            .isSecret(true)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);

        board.complete();

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        given(replyRepository.save(any(Reply.class)))
            .willAnswer(invocation -> {
                Reply reply = invocation.getArgument(0);
                ReflectionTestUtils.setField(reply, "id", 1L);
                return reply;
            });

        // when
        boardService.createReply(
            1L,
            createReplyRequest("추가 문의입니다."),
            authorId
        );

        // then
        assertThat(board.getStatus()).isEqualTo(BoardStatus.PENDING);
        assertThat(board.getReplies()).hasSize(1);

        verify(replyRepository).save(any(Reply.class));
    }

    @Test
    @DisplayName("고객센터 관리자가 댓글을 작성하면 게시글 상태가 COMPLETED로 변경된다")
    void createReply_customerService_admin_test() {
        // given
        Long authorId = 10L;
        Long adminId = 99L;

        Board board = Board.builder()
            .title("고객센터 문의")
            .content("문의 내용")
            .authorId(authorId)
            .boardType(BoardType.CUSTOMER_SERVICE)
            .isSecret(true)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        given(replyRepository.save(any(Reply.class)))
            .willAnswer(invocation -> {
                Reply reply = invocation.getArgument(0);
                ReflectionTestUtils.setField(reply, "id", 1L);
                return reply;
            });

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                adminId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            )
        );

        try {
            // when
            boardService.createReply(
                1L,
                createReplyRequest("관리자 답변입니다."),
                adminId
            );

            // then
            assertThat(board.getStatus()).isEqualTo(BoardStatus.COMPLETED);
            assertThat(board.getReplies()).hasSize(1);

            verify(replyRepository).save(any(Reply.class));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("고객센터 일반 사용자는 다른 사용자의 게시글에 댓글을 작성할 수 없다")
    void createReply_customerService_otherUser_fail() {
        // given
        Long authorId = 10L;
        Long otherUserId = 20L;

        Board board = Board.builder()
            .title("고객센터 문의")
            .content("문의 내용")
            .authorId(authorId)
            .boardType(BoardType.CUSTOMER_SERVICE)
            .isSecret(true)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        // when & then
        assertThatThrownBy(() ->
            boardService.createReply(
                1L,
                createReplyRequest("일반 사용자 댓글입니다."),
                otherUserId
            )
        ).isInstanceOf(ForbiddenException.class);

        verify(replyRepository, never()).save(any(Reply.class));
        assertThat(board.getReplies()).isEmpty();
    }

    @Test
    @DisplayName("마지막 댓글을 삭제하면 게시글 상태가 PENDING으로 변경된다")
    void deleteReply_lastReply_pending_test() {
        // given
        Long authorId = 10L;

        Board board = Board.builder()
            .title("상품 문의")
            .content("문의 내용")
            .authorId(authorId)
            .itemId(100L)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);

        board.complete();

        Reply reply = createReply(board, authorId);

        given(replyRepository.findById(1L))
            .willReturn(Optional.of(reply));

        given(replyRepository.countByBoardId(1L))
            .willReturn(1L);

        given(replyRepository.deleteByIdInBulk(1L))
            .willReturn(1);

        // when
        boardService.deleteReply(1L, authorId);

        // then
        assertThat(board.getStatus()).isEqualTo(BoardStatus.PENDING);

        verify(replyRepository).countByBoardId(1L);
        verify(replyRepository).deleteByIdInBulk(1L);
    }

    @Test
    @DisplayName("댓글이 남아 있으면 댓글 삭제 후에도 게시글 상태가 유지된다")
    void deleteReply_remainingReply_statusMaintained_test() {
        // given
        Long authorId = 10L;

        Board board = Board.builder()
            .title("상품 문의")
            .content("문의 내용")
            .authorId(authorId)
            .itemId(100L)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);

        board.complete();

        Reply deleteTarget = createReply(board, authorId);
        createReply(board, 20L);

        given(replyRepository.findById(1L))
            .willReturn(Optional.of(deleteTarget));

        given(replyRepository.countByBoardId(1L))
            .willReturn(2L);

        given(replyRepository.deleteByIdInBulk(1L))
            .willReturn(1);

        // when
        boardService.deleteReply(1L, authorId);

        // then
        assertThat(board.getStatus()).isEqualTo(BoardStatus.COMPLETED);

        verify(replyRepository).countByBoardId(1L);
        verify(replyRepository).deleteByIdInBulk(1L);
    }

    @Test
    @DisplayName("상품 문의 작성자는 제목, 내용, 비밀글 설정을 수정할 수 있다")
    void updateBoard_productInquiry_test() {
        // given
        Long authorId = 10L;

        Board board = Board.builder()
            .title("기존 상품 문의")
            .content("기존 문의 내용")
            .authorId(authorId)
            .itemId(100L)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .isSecret(false)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        BoardUpdateRequestDto requestDto = BoardUpdateRequestDto.builder()
            .title("수정된 상품 문의")
            .content("수정된 문의 내용")
            .isSecret(true)
            .build();

        // when
        boardService.updateBoard(1L, requestDto, authorId);

        // then
        assertThat(board.getTitle()).isEqualTo("수정된 상품 문의");
        assertThat(board.getContent()).isEqualTo("수정된 문의 내용");
        assertThat(board.getIsSecret()).isTrue();

        verify(boardRepository).findById(1L);
    }

    @Test
    @DisplayName("고객센터 작성자는 제목, 내용, 비밀글 설정을 수정할 수 있다")
    void updateBoard_customerService_test() {
        // given
        Long authorId = 10L;

        Board board = Board.builder()
            .title("기존 고객센터 문의")
            .content("기존 문의 내용")
            .authorId(authorId)
            .boardType(BoardType.CUSTOMER_SERVICE)
            .isSecret(false)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        BoardUpdateRequestDto requestDto = BoardUpdateRequestDto.builder()
            .title("수정된 고객센터 문의")
            .content("수정된 문의 내용")
            .isSecret(true)
            .build();

        // when
        boardService.updateBoard(1L, requestDto, authorId);

        // then
        assertThat(board.getTitle()).isEqualTo("수정된 고객센터 문의");
        assertThat(board.getContent()).isEqualTo("수정된 문의 내용");
        assertThat(board.getIsSecret()).isTrue();
    }

    @Test
    @DisplayName("상품 리뷰는 수정할 때 비밀글로 변경할 수 없다")
    void updateBoard_productReview_secret_test() {
        // given
        Long authorId = 10L;

        Board board = Board.builder()
            .title("기존 상품 리뷰")
            .content("기존 리뷰 내용")
            .authorId(authorId)
            .itemId(100L)
            .boardType(BoardType.PRODUCT_REVIEW)
            .isSecret(false)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        BoardUpdateRequestDto requestDto = BoardUpdateRequestDto.builder()
            .title("수정된 상품 리뷰")
            .content("수정된 리뷰 내용")
            .isSecret(true)
            .build();

        // when
        boardService.updateBoard(1L, requestDto, authorId);

        // then
        assertThat(board.getTitle()).isEqualTo("수정된 상품 리뷰");
        assertThat(board.getContent()).isEqualTo("수정된 리뷰 내용");
        assertThat(board.getIsSecret()).isFalse();
    }

    @Test
    @DisplayName("커뮤니티 게시글은 수정할 때 비밀글로 변경할 수 없다")
    void updateBoard_community_secret_test() {
        // given
        Long authorId = 10L;

        Board board = createBoard(authorId, false);

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        BoardUpdateRequestDto requestDto = BoardUpdateRequestDto.builder()
            .title("수정된 커뮤니티 게시글")
            .content("수정된 커뮤니티 내용")
            .isSecret(true)
            .build();

        // when
        boardService.updateBoard(1L, requestDto, authorId);

        // then
        assertThat(board.getTitle()).isEqualTo("수정된 커뮤니티 게시글");
        assertThat(board.getContent()).isEqualTo("수정된 커뮤니티 내용");
        assertThat(board.getIsSecret()).isFalse();
    }

    @Test
    @DisplayName("상품 문의 작성자는 자신의 게시글을 삭제할 수 있다")
    void deleteBoard_productInquiry_test() {
        // given
        Long authorId = 10L;

        Board board = Board.builder()
            .title("상품 문의")
            .content("상품 문의 내용")
            .authorId(authorId)
            .itemId(100L)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .isSecret(true)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        // when
        boardService.deleteBoard(1L, authorId);

        // then
        verify(boardRepository).delete(board);
    }

    @Test
    @DisplayName("고객센터 작성자는 자신의 게시글을 삭제할 수 있다")
    void deleteBoard_customerService_test() {
        // given
        Long authorId = 10L;

        Board board = Board.builder()
            .title("고객센터 문의")
            .content("문의 내용")
            .authorId(authorId)
            .boardType(BoardType.CUSTOMER_SERVICE)
            .isSecret(true)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        // when
        boardService.deleteBoard(1L, authorId);

        // then
        verify(boardRepository).delete(board);
    }

    @Test
    @DisplayName("상품 리뷰 작성자는 자신의 게시글을 삭제할 수 있다")
    void deleteBoard_productReview_test() {
        // given
        Long authorId = 10L;

        Board board = Board.builder()
            .title("상품 리뷰")
            .content("리뷰 내용")
            .authorId(authorId)
            .itemId(100L)
            .boardType(BoardType.PRODUCT_REVIEW)
            .isSecret(false)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);

        given(boardRepository.findById(1L))
            .willReturn(Optional.of(board));

        // when
        boardService.deleteBoard(1L, authorId);

        // then
        verify(boardRepository).delete(board);
    }
    @Test
    @DisplayName("상품 판매자는 상품 문의 목록에서 게시글 내용을 확인할 수 있다")
    void getBoards_productInquiry_seller_test() {
        // given
        Long authorId = 10L;
        Long sellerId = 20L;

        Board board = createSecretInquiryBoard(authorId);

        Item item = mock(Item.class);
        given(item.getId()).willReturn(100L);
        given(item.getSellerUserId()).willReturn(sellerId);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> boardPage = new PageImpl<>(List.of(board));

        given(boardRepository.findByBoardType(
            BoardType.PRODUCT_INQUIRY,
            pageable
        )).willReturn(boardPage);

        given(itemRepository.findAllById(List.of(100L)))
            .willReturn(List.of(item));

        given(userRepository.findAllById(any()))
            .willReturn(List.of());

        // when
        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.PRODUCT_INQUIRY,
            null,
            pageable,
            sellerId
        );

        // then
        BoardResponseDto response = result.getContent().get(0);

        assertThat(response.getTitle()).isEqualTo("비밀 상품 문의");
        assertThat(response.getContent()).isEqualTo("비밀 문의 내용");
        assertThat(response.getAuthorId()).isEqualTo(authorId);
        assertThat(response.getItemId()).isEqualTo(100L);
    }
    @Test
    @DisplayName("상품 판매자가 아닌 사용자는 상품 문의 내용을 확인할 수 없다")
    void getBoards_productInquiry_nonSeller_test() {
        // given
        Board board = createSecretInquiryBoard(10L);

        Item item = mock(Item.class);
        given(item.getId()).willReturn(100L);
        given(item.getSellerUserId()).willReturn(20L);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> boardPage = new PageImpl<>(List.of(board));

        given(boardRepository.findByBoardType(
            BoardType.PRODUCT_INQUIRY,
            pageable
        )).willReturn(boardPage);

        given(itemRepository.findAllById(List.of(100L)))
            .willReturn(List.of(item));

        given(userRepository.findAllById(any()))
            .willReturn(List.of());

        // when
        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.PRODUCT_INQUIRY,
            null,
            pageable,
            30L
        );

        // then
        BoardResponseDto response = result.getContent().get(0);

        assertThat(response.getTitle()).isEqualTo("비밀글입니다.");
        assertThat(response.getContent()).isNull();
        assertThat(response.getAuthorId()).isNull();
        assertThat(response.getItemId()).isNull();
    }
    @Test
    @DisplayName("관리자는 고객센터 게시글 목록에서 내용을 확인할 수 있다")
    void getBoards_customerService_admin_test() {
        // given
        Long authorId = 10L;
        Long adminId = 99L;

        Board board = Board.builder()
            .title("비밀 고객센터 문의")
            .content("비밀 고객센터 문의 내용")
            .authorId(authorId)
            .boardType(BoardType.CUSTOMER_SERVICE)
            .isSecret(true)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> boardPage = new PageImpl<>(List.of(board));

        given(boardRepository.findByBoardType(
            BoardType.CUSTOMER_SERVICE,
            pageable
        )).willReturn(boardPage);

        given(userRepository.findAllById(any()))
            .willReturn(List.of());

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                adminId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            )
        );

        try {
            // when
            Page<BoardResponseDto> result = boardService.getBoards(
                BoardType.CUSTOMER_SERVICE,
                null,
                pageable,
                adminId
            );

            // then
            BoardResponseDto response = result.getContent().get(0);

            assertThat(response.getTitle()).isEqualTo("비밀 고객센터 문의");
            assertThat(response.getContent()).isEqualTo("비밀 고객센터 문의 내용");
            assertThat(response.getAuthorId()).isEqualTo(authorId);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
    @Test
    @DisplayName("일반 사용자는 다른 사용자의 고객센터 게시글 내용을 확인할 수 없다")
    void getBoards_customerService_otherUser_test() {
        // given
        Board board = Board.builder()
            .title("비밀 고객센터 문의")
            .content("비밀 고객센터 문의 내용")
            .authorId(10L)
            .boardType(BoardType.CUSTOMER_SERVICE)
            .isSecret(true)
            .build();

        ReflectionTestUtils.setField(board, "id", 1L);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Board> boardPage = new PageImpl<>(List.of(board));

        given(boardRepository.findByBoardType(
            BoardType.CUSTOMER_SERVICE,
            pageable
        )).willReturn(boardPage);

        given(userRepository.findAllById(any()))
            .willReturn(List.of());

        // when
        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.CUSTOMER_SERVICE,
            null,
            pageable,
            20L
        );

        // then
        BoardResponseDto response = result.getContent().get(0);

        assertThat(response.getTitle()).isEqualTo("비밀글입니다.");
        assertThat(response.getContent()).isNull();
        assertThat(response.getAuthorId()).isNull();
    }
    @Test
    @DisplayName("게시글 목록 조회 시 전체 개수와 페이지 정보가 유지된다")
    void getBoards_paging_test() {
        // given
        Board board = createBoard(10L, false);

        Pageable pageable = PageRequest.of(1, 10);

        Page<Board> boardPage = new PageImpl<>(
            List.of(board),
            pageable,
            23
        );

        given(boardRepository.findByBoardType(
            BoardType.COMMUNITY,
            pageable
        )).willReturn(boardPage);

        given(userRepository.findAllById(any()))
            .willReturn(List.of());

        // when
        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.COMMUNITY,
            null,
            pageable,
            null
        );

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(23);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.isFirst()).isFalse();
        assertThat(result.isLast()).isFalse();
    }
    @Test
    @DisplayName("게시판 유형이 null이면 게시글 목록 조회에 실패한다")
    void getBoards_nullBoardType_fail() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        // when & then
        assertThatThrownBy(() ->
            boardService.getBoards(null, null, pageable, 1L)
        ).isInstanceOf(BadRequestException.class)
            .hasMessage("지원하지 않는 게시판 유형입니다.");

        verifyNoInteractions(boardRepository);
    }
}
