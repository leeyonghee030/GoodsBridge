
package com.goodsbridge.board;

import com.goodsbridge._core.exception.ForbiddenException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BoardIntegrationTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private ReplyRepository replyRepository;

    @Autowired
    private EntityManager em;


    // 게시글 생성 요청 DTO
    private BoardRequestDto createBoardRequest(
        String title,
        String content,
        Boolean isSecret
    ) {
        return BoardRequestDto.builder()
            .title(title)
            .content(content)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .itemId(1L)
            .isSecret(isSecret)
            .build();
    }


    // 게시글 수정 요청 DTO
    private BoardUpdateRequestDto createBoardUpdateRequest(
        String title,
        String content,
        Boolean isSecret
    ) {
        return BoardUpdateRequestDto.builder()
            .title(title)
            .content(content)
            .isSecret(isSecret)
            .build();
    }

    // 댓글 요청 DTO 생성
    private ReplyRequestDto createReplyRequest(String content) {
        ReplyRequestDto requestDto = new ReplyRequestDto();
        ReflectionTestUtils.setField(requestDto, "content", content);
        return requestDto;
    }

    @Test
    @DisplayName("게시글 생성 후 실제 DB에 저장된다")
    void createBoard_integration_test() {
        // given
        Long loginUserId = 1L;

        BoardRequestDto requestDto = createBoardRequest(
            "통합 테스트 제목",
            "통합 테스트 내용",
            false
        );

        // when
        Long boardId = boardService.createBoard(requestDto, loginUserId);

        em.flush();
        em.clear();

        // then
        Board board = boardRepository.findById(boardId)
            .orElseThrow();

        assertThat(board.getTitle()).isEqualTo("통합 테스트 제목");
        assertThat(board.getContent()).isEqualTo("통합 테스트 내용");
        assertThat(board.getAuthorId()).isEqualTo(loginUserId);
        assertThat(board.getIsSecret()).isFalse();
        assertThat(board.getStatus()).isEqualTo(BoardStatus.PENDING);
    }

    @Test
    @DisplayName("게시글 작성자는 게시글을 수정하고 삭제할 수 있다")
    void updateAndDeleteBoard_integration_test() {
        // given
        Long loginUserId = 1L;

        Long boardId = boardService.createBoard(
            createBoardRequest("기존 제목", "기존 내용", false),
            loginUserId
        );

        // when - 게시글 수정
        boardService.updateBoard(
            boardId,
            createBoardUpdateRequest("수정된 제목", "수정된 내용", true),
            loginUserId
        );

        em.flush();
        em.clear();

        // then - 수정 결과 확인
        Board updatedBoard = boardRepository.findById(boardId)
            .orElseThrow();

        assertThat(updatedBoard.getTitle()).isEqualTo("수정된 제목");
        assertThat(updatedBoard.getContent()).isEqualTo("수정된 내용");
        assertThat(updatedBoard.getBoardType())
            .isEqualTo(BoardType.PRODUCT_INQUIRY);

        assertThat(updatedBoard.getItemId())
            .isEqualTo(1L);
        assertThat(updatedBoard.getIsSecret()).isTrue();

        // when - 게시글 삭제
        boardService.deleteBoard(boardId, loginUserId);

        em.flush();
        em.clear();

        // then - 삭제 확인
        assertThat(boardRepository.findById(boardId)).isEmpty();
    }

    @Test
    @DisplayName("댓글 작성 시 게시글 상태가 COMPLETED로 변경된다")
    void createReply_changesBoardStatus_integration_test() {
        // given
        Long authorId = 1L;

        Long boardId = boardService.createBoard(
            createBoardRequest("문의 제목", "문의 내용", false),
            authorId
        );

        // when
        Long replyId = boardService.createReply(
            boardId,
            createReplyRequest("답변 내용"),
            2L
        );

        em.flush();
        em.clear();

        // then
        Board board = boardRepository.findById(boardId)
            .orElseThrow();

        assertThat(replyRepository.findById(replyId)).isPresent();
        assertThat(replyRepository.countByBoardId(boardId)).isEqualTo(1);
        assertThat(board.getStatus()).isEqualTo(BoardStatus.COMPLETED);
    }

    @Test
    @DisplayName("마지막 댓글 삭제 시 게시글 상태가 PENDING으로 변경되고 댓글도 삭제된다")
    void deleteLastReply_changesBoardStatus_integration_test() {
        // given
        Long authorId = 1L;
        Long replyAuthorId = 2L;

        Long boardId = boardService.createBoard(
            createBoardRequest("문의 제목", "문의 내용", false),
            authorId
        );

        Long replyId = boardService.createReply(
            boardId,
            createReplyRequest("답변 내용"),
            replyAuthorId
        );

        // when
        boardService.deleteReply(replyId, replyAuthorId);

        em.flush();
        em.clear();

        // then
        Board board = boardRepository.findById(boardId)
            .orElseThrow();

        assertThat(replyRepository.findById(replyId)).isEmpty();
        assertThat(replyRepository.countByBoardId(boardId)).isZero();
        assertThat(board.getStatus()).isEqualTo(BoardStatus.PENDING);
    }

    @Test
    @DisplayName("비밀글은 게시글 작성자만 조회할 수 있다")
    void getSecretBoard_authorization_integration_test() {
        // given
        Long authorId = 1L;
        Long otherUserId = 2L;

        Long boardId = boardService.createBoard(
            createBoardRequest("비밀글 제목", "비밀글 내용", true),
            authorId
        );

        em.flush();
        em.clear();

        // when & then - 작성자는 조회 가능
        assertThat(boardService.getBoard(boardId, authorId).getTitle())
            .isEqualTo("비밀글 제목");

        // when & then - 다른 사용자는 조회 불가
        assertThatThrownBy(() ->
            boardService.getBoard(boardId, otherUserId)
        ).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("비밀글에는 게시글 작성자만 댓글을 작성할 수 있다")
    void createReply_secretBoard_authorization_integration_test() {
        // given
        Long authorId = 1L;
        Long otherUserId = 2L;

        Long boardId = boardService.createBoard(
            createBoardRequest("비밀글 제목", "비밀글 내용", true),
            authorId
        );

        // when & then - 다른 사용자는 댓글 작성 불가
        assertThatThrownBy(() ->
            boardService.createReply(
                boardId,
                createReplyRequest("다른 사용자의 댓글"),
                otherUserId
            )
        ).isInstanceOf(ForbiddenException.class);

        // then - 작성자만 댓글 작성 가능
        Long replyId = boardService.createReply(
            boardId,
            createReplyRequest("작성자의 댓글"),
            authorId
        );

        em.flush();
        em.clear();

        assertThat(replyRepository.findById(replyId)).isPresent();
        assertThat(replyRepository.countByBoardId(boardId)).isEqualTo(1);
    }


    @Test
    @DisplayName("댓글이 여러 개일 때 하나만 삭제하면 게시글 상태는 COMPLETED를 유지한다")
    void deleteOneReply_keepsCompletedStatus_integration_test() {
        // given
        Long boardAuthorId = 1L;
        Long firstReplyAuthorId = 2L;
        Long secondReplyAuthorId = 3L;

        Long boardId = boardService.createBoard(
            createBoardRequest("문의 제목", "문의 내용", false),
            boardAuthorId
        );

        Long firstReplyId = boardService.createReply(
            boardId,
            createReplyRequest("첫 번째 댓글"),
            firstReplyAuthorId
        );

        Long secondReplyId = boardService.createReply(
            boardId,
            createReplyRequest("두 번째 댓글"),
            secondReplyAuthorId
        );

        // when
        boardService.deleteReply(firstReplyId, firstReplyAuthorId);

        em.flush();
        em.clear();

        // then
        Board board = boardRepository.findById(boardId)
            .orElseThrow();

        assertThat(replyRepository.findById(firstReplyId)).isEmpty();
        assertThat(replyRepository.findById(secondReplyId)).isPresent();

        assertThat(replyRepository.countByBoardId(boardId)).isEqualTo(1);
        assertThat(board.getStatus()).isEqualTo(BoardStatus.COMPLETED);
    }


    @Test
    @DisplayName("게시글 삭제 시 연관된 댓글도 함께 삭제된다")
    void deleteBoard_deletesReplies_integration_test() {
        // given
        Long boardAuthorId = 1L;
        Long firstReplyAuthorId = 2L;
        Long secondReplyAuthorId = 3L;

        Long boardId = boardService.createBoard(
            createBoardRequest("문의 제목", "문의 내용", false),
            boardAuthorId
        );

        Long firstReplyId = boardService.createReply(
            boardId,
            createReplyRequest("첫 번째 댓글"),
            firstReplyAuthorId
        );

        Long secondReplyId = boardService.createReply(
            boardId,
            createReplyRequest("두 번째 댓글"),
            secondReplyAuthorId
        );

        em.flush();
        em.clear();

        // when
        boardService.deleteBoard(boardId, boardAuthorId);

        em.flush();
        em.clear();

        // then
        assertThat(boardRepository.findById(boardId)).isEmpty();

        assertThat(replyRepository.findById(firstReplyId)).isEmpty();
        assertThat(replyRepository.findById(secondReplyId)).isEmpty();

        assertThat(replyRepository.countByBoardId(boardId)).isZero();
    }


}
