package com.goodsbridge.board;


import com.goodsbridge._core.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private ReplyRepository replyRepository;

    @InjectMocks
    private BoardService boardService;

    @Test
    @DisplayName("게시글 작성 성공")
    void createBoard_success() {
        // given
        BoardRequestDto requestDto = new BoardRequestDto();
        // (실제 테스트 시 롬복 Builder나 Setter로 값 세팅 가정)

        Board savedBoard = Board.builder()
            .title("테스트 제목")
            .content("테스트 내용")
            .authorId(1L)
            .itemId(100L)
            .isSecret(false)
            .build();

        given(boardRepository.save(any(Board.class))).willReturn(savedBoard);

        // when
        Long boardId = boardService.createBoard(requestDto, 1L);

        // then
        // assertThat(boardId).isNotNull();
        verify(boardRepository).save(any(Board.class));
    }

    @Test
    @DisplayName("비밀글 상세 조회 실패 - 작성자가 아니면 403 Forbidden 예외 발생")
    void getBoard_secret_forbidden() {
        // given
        Long boardId = 1L;
        Long loginUserId = 2L; // 다른 사용자

        Board secretBoard = Board.builder()
            .title("비밀글")
            .content("내용")
            .authorId(1L) // 작성자는 1번
            .isSecret(true)
            .build();

        given(boardRepository.findByIdWithReplies(boardId)).willReturn(Optional.of(secretBoard));

        // when & then
        assertThatThrownBy(() -> boardService.getBoard(boardId, loginUserId))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("댓글 작성 성공 및 게시판 상태 답변 완료(COMPLETED) 변경 확인")
    void createReply_success() {
        // given
        Long boardId = 1L;
        Long loginUserId = 2L;

        Board board = Board.builder()
            .title("제목")
            .content("내용")
            .authorId(1L)
            .build();

        ReplyRequestDto requestDto = new ReplyRequestDto();

        given(boardRepository.findById(boardId)).willReturn(Optional.of(board));
        given(replyRepository.save(any(Reply.class))).willReturn(
            Reply.builder().board(board).content("댓글").authorId(loginUserId).build()
        );

        // when
        boardService.createReply(boardId, requestDto, loginUserId);

        // then
        assertThat(board.getStatus()).isEqualTo(BoardStatus.COMPLETED);
    }

    @Test
    @DisplayName("게시글 삭제 실패 - 작성자가 아닌 경우 403 Forbidden")
    void deleteBoard_forbidden() {
        // given
        Long boardId = 1L;
        Long loginUserId = 2L; // 로그인 유저

        Board board = Board.builder()
            .title("제목")
            .content("내용")
            .authorId(1L) // 작성자
            .build();

        given(boardRepository.findById(boardId)).willReturn(Optional.of(board));

        // when & then
        assertThatThrownBy(() -> boardService.deleteBoard(boardId, loginUserId))
            .isInstanceOf(ForbiddenException.class);
    }
}
