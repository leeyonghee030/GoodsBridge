
package com.goodsbridge.board;

import com.goodsbridge.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
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
        BoardType boardType,
        Long itemId,
        Boolean isSecret
    ) {
        return Board.builder()
            .title(title)
            .content(content)
            .authorId(authorId)
            .boardType(boardType)
            .itemId(itemId)
            .isSecret(isSecret)
            .build();
    }

    private User createUser(String email, String nickname) {
        return User.builder()
            .email(email)
            .password("1234")
            .nickname(nickname)
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
            BoardType.COMMUNITY,
            null,
            false
        );

        boardRepository.save(board);
        em.flush();

        Long boardId = board.getId();
        em.clear();

        // when
        Optional<Board> result = boardRepository.findById(boardId);

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
            BoardType.COMMUNITY,
            null,
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
    @DisplayName("게시글 목록을 두 번째 페이지까지 페이징 조회한다")
    void findAll_second_page_test() {
        // given
        boardRepository.save(createBoard(
            "첫 번째", "내용 1", 1L,
            BoardType.COMMUNITY, null, false
        ));

        boardRepository.save(createBoard(
            "두 번째", "내용 2", 1L,
            BoardType.COMMUNITY, null, false
        ));

        boardRepository.save(createBoard(
            "세 번째", "내용 3", 1L,
            BoardType.COMMUNITY, null, false
        ));

        em.flush();
        em.clear();

        // when
        Page<Board> firstPage = boardRepository.findAll(
            PageRequest.of(
                0,
                2,
                Sort.by(Sort.Direction.DESC, "id")
            )
        );

        Page<Board> secondPage = boardRepository.findAll(
            PageRequest.of(
                1,
                2,
                Sort.by(Sort.Direction.DESC, "id")
            )
        );

        // then
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();

        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.hasNext()).isFalse();

        assertThat(secondPage.getContent())
            .extracting(Board::getTitle)
            .containsExactly("첫 번째");
    }

    @Test
    @DisplayName("특정 게시판 유형의 전체 게시글을 조회한다")
    void findByBoardType_test() {
        // given
        boardRepository.save(createBoard(
            "첫 번째 고객센터 문의",
            "문의 내용 1",
            1L,
            BoardType.CUSTOMER_SERVICE,
            null,
            true
        ));

        boardRepository.save(createBoard(
            "두 번째 고객센터 문의",
            "문의 내용 2",
            2L,
            BoardType.CUSTOMER_SERVICE,
            null,
            true
        ));

        boardRepository.save(createBoard(
            "커뮤니티 게시글",
            "게시글 내용",
            3L,
            BoardType.COMMUNITY,
            null,
            false
        ));

        em.flush();
        em.clear();

        // when
        Page<Board> result = boardRepository.findByBoardType(
            BoardType.CUSTOMER_SERVICE,
            PageRequest.of(0, 10)
        );

        // then
        assertThat(result.getTotalElements()).isEqualTo(2);

        assertThat(result.getContent())
            .extracting(Board::getTitle)
            .containsExactlyInAnyOrder(
                "첫 번째 고객센터 문의",
                "두 번째 고객센터 문의"
            );

        assertThat(result.getContent())
            .allSatisfy(board ->
                assertThat(board.getBoardType())
                    .isEqualTo(BoardType.CUSTOMER_SERVICE)
            );
    }

    @Test
    @DisplayName("게시글 제목, 내용, 작성자 닉네임으로 게시글을 검색한다")
    void findByKeyword_test() {
        // given
        User user1 = createUser("user1@test.com", "검색왕");
        User user2 = createUser("user2@test.com", "일반회원");

        em.persist(user1);
        em.persist(user2);

        em.flush();

        boardRepository.save(createBoard(
            "전자제품 구매 문의",
            "상품에 대해 궁금합니다.",
            user1.getId(),
            BoardType.PRODUCT_INQUIRY,
            100L,
            true
        ));

        boardRepository.save(createBoard(
            "일반 게시글",
            "전자제품 사용 후기입니다.",
            user2.getId(),
            BoardType.COMMUNITY,
            null,
            false
        ));

        boardRepository.save(createBoard(
            "오늘의 이야기",
            "즐거운 하루입니다.",
            user1.getId(),
            BoardType.COMMUNITY,
            null,
            false
        ));

        em.flush();
        em.clear();

        // when
        Page<Board> result = boardRepository.findByKeyword(
            "검색왕",
            List.of(
                BoardType.PRODUCT_INQUIRY,
                BoardType.COMMUNITY
            ),
            PageRequest.of(0, 10)
        );

        // then
        assertThat(result.getTotalElements()).isEqualTo(2);

        assertThat(result.getContent())
            .extracting(Board::getTitle)
            .containsExactlyInAnyOrder(
                "전자제품 구매 문의",
                "오늘의 이야기"
            );

        assertThat(result.getContent())
            .allSatisfy(board ->
                assertThat(board.getAuthorId()).isEqualTo(user1.getId())
            );
    }

    @Test
    @DisplayName("제목에 검색어가 포함된 게시글을 조회한다")
    void findByKeyword_title_test() {
        // given
        User user = createUser("title@test.com", "작성자");
        em.persist(user);
        em.flush();

        boardRepository.save(createBoard(
            "상품 문의합니다",
            "일반적인 내용",
            user.getId(),
            BoardType.COMMUNITY,
            null,
            false
        ));

        boardRepository.save(createBoard(
            "일반 문의",
            "다른 내용",
            user.getId(),
            BoardType.COMMUNITY,
            null,
            false
        ));

        em.flush();
        em.clear();

        // when
        Page<Board> result = boardRepository.findByKeyword(
            "상품",
            List.of(BoardType.COMMUNITY),
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
    void findByKeyword_content_test() {
        // given
        User user = createUser("content@test.com", "작성자");
        em.persist(user);
        em.flush();

        boardRepository.save(createBoard(
            "첫 번째 게시글",
            "배송이 궁금합니다",
            user.getId(),
            BoardType.COMMUNITY,
            null,
            false
        ));

        boardRepository.save(createBoard(
            "두 번째 게시글",
            "상품 문의입니다",
            user.getId(),
            BoardType.COMMUNITY,
            null,
            false
        ));

        em.flush();
        em.clear();

        // when
        Page<Board> result = boardRepository.findByKeyword(
            "배송",
            List.of(BoardType.COMMUNITY),
            PageRequest.of(0, 10)
        );

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);

        assertThat(result.getContent())
            .extracting(Board::getContent)
            .containsExactly("배송이 궁금합니다");
    }

    @Test
    @DisplayName("검색 시 지정한 게시판 유형에 해당하는 게시글만 조회한다")
    void findByKeyword_boardType_test() {
        // given
        User user = createUser("type@test.com", "검색왕");
        em.persist(user);
        em.flush();

        boardRepository.save(createBoard(
            "검색 대상 커뮤니티",
            "커뮤니티 내용",
            user.getId(),
            BoardType.COMMUNITY,
            null,
            false
        ));

        boardRepository.save(createBoard(
            "검색 대상 상품 리뷰",
            "리뷰 내용",
            user.getId(),
            BoardType.PRODUCT_REVIEW,
            100L,
            false
        ));

        boardRepository.save(createBoard(
            "검색 대상 고객센터",
            "고객센터 내용",
            user.getId(),
            BoardType.CUSTOMER_SERVICE,
            null,
            true
        ));

        em.flush();
        em.clear();

        // when
        Page<Board> result = boardRepository.findByKeyword(
            "검색 대상",
            List.of(
                BoardType.COMMUNITY,
                BoardType.PRODUCT_REVIEW
            ),
            PageRequest.of(0, 10)
        );

        // then
        assertThat(result.getTotalElements()).isEqualTo(2);

        assertThat(result.getContent())
            .extracting(Board::getBoardType)
            .containsExactlyInAnyOrder(
                BoardType.COMMUNITY,
                BoardType.PRODUCT_REVIEW
            );

        assertThat(result.getContent())
            .noneMatch(board ->
                board.getBoardType() == BoardType.CUSTOMER_SERVICE
            );
    }
}
