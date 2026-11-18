
package com.goodsbridge.board;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;


import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ReplyRepositoryTest {

    @Autowired
    private ReplyRepository replyRepository;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private EntityManager em;

    private Board createBoard() {
        return Board.builder()
            .title("게시글 제목")
            .content("게시글 내용")
            .authorId(1L)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .itemId(1L)
            .isSecret(false)
            .build();
    }

    @Test
    @DisplayName("게시글 ID로 해당 게시글의 댓글 목록을 조회한다")
    void findAllByBoardId_test() {
        // given
        Board board = createBoard();

        boardRepository.save(board);

        Reply reply1 = Reply.builder()
            .content("댓글 1")
            .authorId(2L)
            .build();

        Reply reply2 = Reply.builder()
            .content("댓글 2")
            .authorId(3L)
            .build();

        board.addReply(reply1);
        board.addReply(reply2);

        replyRepository.saveAll(List.of(reply1, reply2));

        em.flush();

        Long boardId = board.getId();

        em.clear();

        // when
        List<Reply> result =
            replyRepository.findAllByBoardId(boardId);

        // then
        assertThat(result).hasSize(2);

        assertThat(result)
            .extracting(Reply::getContent)
            .containsExactlyInAnyOrder("댓글 1", "댓글 2");
    }

    @Test
    @DisplayName("게시글 ID로 댓글 개수를 조회한다")
    void countByBoardId_test() {
        // given
        Board board = createBoard();

        boardRepository.save(board);

        Reply reply1 = Reply.builder()
            .content("댓글 1")
            .authorId(2L)
            .build();

        Reply reply2 = Reply.builder()
            .content("댓글 2")
            .authorId(3L)
            .build();

        board.addReply(reply1);
        board.addReply(reply2);

        replyRepository.saveAll(List.of(reply1, reply2));

        em.flush();
        em.clear();

        // when
        long count = replyRepository.countByBoardId(board.getId());

        // then
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("댓글이 없는 게시글은 빈 댓글 목록을 반환한다")
    void findAllByBoardId_empty_test() {
        // given
        Board board = createBoard();
        boardRepository.save(board);

        em.flush();
        em.clear();

        // when
        List<Reply> result =
            replyRepository.findAllByBoardId(board.getId());

        // then
        assertThat(result).isEmpty();
    }
}
