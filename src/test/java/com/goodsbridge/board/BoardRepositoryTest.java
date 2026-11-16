
package com.goodsbridge.board;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class BoardRepositoryTest {

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private EntityManager em;

    private Board createBoard(
        String title,
        String content,
        Long authorId,
        Boolean isSecret
    ) {
        return Board.builder()
            .title(title)
            .content(content)
            .authorId(authorId)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .itemId(1L)
            .isSecret(isSecret)
            .build();
    }

    @Test
    @DisplayName("게시글 ID로 조회하면 게시글을 반환한다")
    void findById_test() {
        // given
        Board board = createBoard(
            "테스트 제목",
            "테스트 내용",
            1L,
            false
        );

        boardRepository.save(board);
        em.flush();
        em.clear();

        // when
        Optional<Board> result =
            boardRepository.findById(board.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("테스트 제목");
        assertThat(result.get().getAuthorId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("게시글 상세 조회 시 댓글도 함께 조회한다")
    void findByIdWithReplies_test() {
        // given
        Board board = createBoard(
            "게시글 제목",
            "게시글 내용",
            1L,
            false
        );

        Reply reply1 = Reply.builder()
            .content("첫 번째 댓글")
            .authorId(2L)
            .build();

        Reply reply2 = Reply.builder()
            .content("두 번째 댓글")
            .authorId(3L)
            .build();

        board.addReply(reply1);
        board.addReply(reply2);

        boardRepository.save(board);

        em.flush();

        Long boardId = board.getId();

        em.clear();

        // when
        Optional<Board> result =
            boardRepository.findByIdWithReplies(boardId);

        // then
        assertThat(result).isPresent();

        Board findBoard = result.get();

        assertThat(findBoard.getTitle()).isEqualTo("게시글 제목");
        assertThat(findBoard.getReplies()).hasSize(2);

        assertThat(findBoard.getReplies())
            .extracting(Reply::getContent)
            .containsExactlyInAnyOrder(
                "첫 번째 댓글",
                "두 번째 댓글"
            );
    }

    @Test
    @DisplayName("제목에 검색어가 포함된 게시글을 조회한다")
    void findByTitleContaining_test() {
        // given
        boardRepository.save(createBoard(
            "상품 문의합니다",
            "내용 1",
            1L,
            false
        ));

        boardRepository.save(createBoard(
            "일반 문의",
            "내용 2",
            2L,
            false
        ));

        em.flush();
        em.clear();

        // when
        Page<Board> result =
            boardRepository
                .findByTitleContainingOrContentContaining(
                    "상품",
                    "상품",
                    PageRequest.of(0, 10)
                );

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
            .extracting(Board::getTitle)
            .containsExactly("상품 문의합니다");
    }

    @Test
    @DisplayName("내용에 검색어가 포함된 게시글을 조회한다")
    void findByContentContaining_test() {
        // given
        boardRepository.save(createBoard(
            "첫 번째 게시글",
            "배송이 궁금합니다",
            1L,
            false
        ));

        boardRepository.save(createBoard(
            "두 번째 게시글",
            "상품 문의입니다",
            2L,
            false
        ));

        em.flush();
        em.clear();

        // when
        Page<Board> result =
            boardRepository
                .findByTitleContainingOrContentContaining(
                    "배송",
                    "배송",
                    PageRequest.of(0, 10)
                );

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
            .extracting(Board::getContent)
            .containsExactly("배송이 궁금합니다");
    }

    @Test
    @DisplayName("게시글 목록을 ID 내림차순으로 페이징 조회한다")
    void findAll_page_test() {
        // given
        boardRepository.save(createBoard("첫 번째", "내용 1", 1L, false));
        boardRepository.save(createBoard("두 번째", "내용 2", 1L, false));
        boardRepository.save(createBoard("세 번째", "내용 3", 1L, false));

        em.flush();
        em.clear();

        // when
        Page<Board> result = boardRepository.findAll(
            PageRequest.of(
                0,
                2,
                Sort.by(Sort.Direction.DESC, "id")
            )
        );

        // then
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(2);

        assertThat(result.getContent())
            .extracting(Board::getTitle)
            .containsExactly("세 번째", "두 번째");
    }

    @Test
    @DisplayName("상품 ID와 게시글 유형으로 상품 문의 게시글을 조회한다")
    void findByItemIdAndBoardType_test() {
        // given
        boardRepository.save(Board.builder()
            .title("상품 문의 1")
            .content("문의 내용 1")
            .authorId(1L)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .itemId(100L)
            .isSecret(false)
            .build());

        boardRepository.save(Board.builder()
            .title("상품 문의 2")
            .content("문의 내용 2")
            .authorId(2L)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .itemId(100L)
            .isSecret(false)
            .build());

        boardRepository.save(Board.builder()
            .title("다른 상품 문의")
            .content("문의 내용 3")
            .authorId(3L)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .itemId(200L)
            .isSecret(false)
            .build());

        em.flush();
        em.clear();

        // when
        Page<Board> result = boardRepository.findByItemIdAndBoardType(
            100L,
            BoardType.PRODUCT_INQUIRY,
            PageRequest.of(0, 10)
        );

        // then
        assertThat(result.getTotalElements()).isEqualTo(2);

        assertThat(result.getContent())
            .extracting(Board::getTitle)
            .containsExactlyInAnyOrder(
                "상품 문의 1",
                "상품 문의 2"
            );

        assertThat(result.getContent())
            .allSatisfy(board -> {
                assertThat(board.getItemId()).isEqualTo(100L);
                assertThat(board.getBoardType())
                    .isEqualTo(BoardType.PRODUCT_INQUIRY);
            });
    }
}
