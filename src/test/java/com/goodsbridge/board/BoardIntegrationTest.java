
package com.goodsbridge.board;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.*;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    private ItemRepository itemRepository;

    @Autowired
    private CategoryJpaRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager em;

    private Long itemId;
    private Long sellerId = 2L;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(
            Category.builder()
                .name("통합 테스트 카테고리")
                .build()
        );

        Item item = itemRepository.save(
            Item.builder()
                .sellerUserId(sellerId)
                .category(category)
                .title("통합 테스트 상품")
                .description("통합 테스트 상품 설명")
                .price(10000)
                .saleType(SaleType.NORMAL)
                .status(ItemStatus.ON_SALE)
                .build()
        );

        itemId = item.getId();
    }

    // 상품 문의 게시글 생성 요청 DTO
    private BoardRequestDto createBoardRequest(
        String title,
        String content,
        Boolean isSecret
    ) {
        return BoardRequestDto.builder()
            .title(title)
            .content(content)
            .boardType(BoardType.PRODUCT_INQUIRY)
            .itemId(itemId)
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

    private Board createLegacyBoard(Long authorId) {
        Board board = Board.builder()
            .title("기존 게시글")
            .content("기존 게시글 내용")
            .authorId(authorId)
            .itemId(null)
            .boardType(BoardType.LEGACY)
            .isSecret(false)
            .build();

        return boardRepository.save(board);
    }

    // 고객센터 게시글 생성 요청 DTO
    private BoardRequestDto createCustomerServiceRequest(
        String title,
        String content,
        Boolean isSecret
    ) {
        return BoardRequestDto.builder()
            .title(title)
            .content(content)
            .boardType(BoardType.CUSTOMER_SERVICE)
            .itemId(null)
            .isSecret(isSecret)
            .build();
    }

    // 테스트용 관리자 인증 설정
    private void loginAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                99L,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
        assertThat(board.getBoardType()).isEqualTo(BoardType.PRODUCT_INQUIRY);
        assertThat(board.getItemId()).isEqualTo(itemId);
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
        assertThat(updatedBoard.getBoardType()).isEqualTo(BoardType.PRODUCT_INQUIRY);
        assertThat(updatedBoard.getItemId()).isEqualTo(itemId);
        assertThat(updatedBoard.getIsSecret()).isTrue();

        // when - 게시글 삭제
        boardService.deleteBoard(boardId, loginUserId);

        em.flush();
        em.clear();

        // then - 삭제 확인
        assertThat(boardRepository.findById(boardId)).isEmpty();
    }

    @Test
    @DisplayName("상품 판매자가 댓글을 작성하면 게시글 상태가 COMPLETED로 변경된다")
    void createReply_changesBoardStatus_integration_test() {
        // given
        Long authorId = 1L;

        Long boardId = boardService.createBoard(
            createBoardRequest("문의 제목", "문의 내용", false),
            authorId
        );

        // when - 상품 판매자가 답변 작성
        Long replyId = boardService.createReply(
            boardId,
            createReplyRequest("판매자 답변 내용"),
            sellerId
        );

        em.flush();
        em.clear();

        // then
        Board board = boardRepository.findById(boardId)
            .orElseThrow();

        assertThat(replyRepository.findById(replyId)).isPresent();
        assertThat(countRepliesByBoardId(boardId)).isEqualTo(1);
        assertThat(board.getStatus()).isEqualTo(BoardStatus.COMPLETED);
    }

    @Test
    @DisplayName("마지막 댓글 삭제 시 게시글 상태가 PENDING으로 변경되고 댓글도 삭제된다")
    void deleteLastReply_changesBoardStatus_integration_test() {
        // given
        Long authorId = 1L;

        Long boardId = boardService.createBoard(
            createBoardRequest("문의 제목", "문의 내용", false),
            authorId
        );

        Long replyId = boardService.createReply(
            boardId,
            createReplyRequest("판매자 답변 내용"),
            sellerId
        );

        // when
        boardService.deleteReply(replyId, sellerId);

        em.flush();
        em.clear();

        // then
        Board board = boardRepository.findById(boardId)
            .orElseThrow();

        assertThat(replyRepository.findById(replyId)).isEmpty();
        assertThat(countRepliesByBoardId(boardId)).isZero();
        assertThat(board.getStatus()).isEqualTo(BoardStatus.PENDING);
    }

    @Test
    @DisplayName("비밀 상품 문의는 작성자와 상품 판매자만 조회할 수 있다")
    void getSecretBoard_authorization_integration_test() {
        // given
        Long authorId = 1L;
        Long otherUserId = 3L;

        Long boardId = boardService.createBoard(
            createBoardRequest("비밀글 제목", "비밀글 내용", true),
            authorId
        );

        em.flush();
        em.clear();

        // when & then - 작성자는 조회 가능
        assertThat(boardService.getBoard(boardId, authorId).getTitle())
            .isEqualTo("비밀글 제목");

        // 상품 판매자도 조회 가능
        assertThat(boardService.getBoard(boardId, sellerId).getTitle())
            .isEqualTo("비밀글 제목");

        // 다른 사용자는 조회 불가
        assertThatThrownBy(() ->
            boardService.getBoard(boardId, otherUserId)
        ).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("비밀 상품 문의에는 작성자와 상품 판매자만 댓글을 작성할 수 있다")
    void createReply_secretBoard_authorization_integration_test() {
        // given
        Long authorId = 1L;
        Long otherUserId = 3L;

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

        // 상품 판매자는 댓글 작성 가능
        Long replyId = boardService.createReply(
            boardId,
            createReplyRequest("판매자 답변"),
            sellerId
        );

        em.flush();
        em.clear();

        assertThat(replyRepository.findById(replyId)).isPresent();
        assertThat(countRepliesByBoardId(boardId)).isEqualTo(1);
    }

    @Test
    @DisplayName("댓글이 여러 개일 때 하나만 삭제하면 게시글 상태는 COMPLETED를 유지한다")
    void deleteOneReply_keepsCompletedStatus_integration_test() {

        Long boardAuthorId = 1L;

        Long boardId = boardService.createBoard(
            createBoardRequest("문의 제목", "문의 내용", false),
            boardAuthorId
        );

        // 판매자 댓글 두 개 작성
        Long firstReplyId = boardService.createReply(
            boardId,
            createReplyRequest("첫 번째 판매자 답변"),
            sellerId
        );

        Long secondReplyId = boardService.createReply(
            boardId,
            createReplyRequest("두 번째 판매자 답변"),
            sellerId
        );

        // 첫 번째 댓글 삭제
        boardService.deleteReply(firstReplyId, sellerId);

        em.flush();
        em.clear();

        Board board = boardRepository.findById(boardId).orElseThrow();

        // 첫 번째 댓글 삭제 확인
        assertThat(replyRepository.findById(firstReplyId)).isEmpty();

        // 두 번째 댓글은 남아 있어야 함
        assertThat(replyRepository.findById(secondReplyId)).isPresent();

        // 게시글 상태는 답변 완료 유지
        assertThat(board.getStatus()).isEqualTo(BoardStatus.COMPLETED);
    }

    @Test
    @DisplayName("게시글 삭제 시 연관된 댓글도 함께 삭제된다")
    void deleteBoard_deletesReplies_integration_test() {
        // given
        Long boardAuthorId = 1L;

        Long boardId = boardService.createBoard(
            createBoardRequest("문의 제목", "문의 내용", false),
            boardAuthorId
        );

        Long firstReplyId = boardService.createReply(
            boardId,
            createReplyRequest("판매자 답변"),
            sellerId
        );

        Long secondReplyId = boardService.createReply(
            boardId,
            createReplyRequest("작성자 추가 댓글"),
            boardAuthorId
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
        assertThat(countRepliesByBoardId(boardId)).isZero();
    }

    private long countRepliesByBoardId(Long boardId) {
        return replyRepository.findAll().stream()
            .filter(reply -> reply.getBoard().getId().equals(boardId))
            .count();
    }

    @Test
    @DisplayName("비밀 상품 문의 작성자는 댓글을 작성할 수 있다")
    void createReply_secretBoard_author_test() {
        // given
        Long authorId = 1L;

        Long boardId = boardService.createBoard(
            createBoardRequest("비밀글 제목", "비밀글 내용", true),
            authorId
        );

        // when
        Long replyId = boardService.createReply(
            boardId,
            createReplyRequest("작성자 추가 문의"),
            authorId
        );

        em.flush();
        em.clear();

        // then
        Board board = boardRepository.findById(boardId)
            .orElseThrow();

        Reply reply = replyRepository.findById(replyId)
            .orElseThrow();

        assertThat(reply.getAuthorId()).isEqualTo(authorId);
        assertThat(board.getReplies()).hasSize(1);
        assertThat(board.getStatus()).isEqualTo(BoardStatus.PENDING);
    }

    @Test
    @DisplayName("판매자 답변 후 작성자가 추가 댓글을 작성하면 PENDING으로 변경된다")
    void createAuthorReply_changesBoardStatusToPending_test() {
        // given
        Long authorId = 1L;

        Long boardId = boardService.createBoard(
            createBoardRequest("문의 제목", "문의 내용", true),
            authorId
        );

        boardService.createReply(
            boardId,
            createReplyRequest("판매자 답변"),
            sellerId
        );

        // when
        boardService.createReply(
            boardId,
            createReplyRequest("추가 문의"),
            authorId
        );

        em.flush();
        em.clear();

        // then
        Board board = boardRepository.findById(boardId)
            .orElseThrow();

        assertThat(board.getStatus()).isEqualTo(BoardStatus.PENDING);
        assertThat(board.getReplies()).hasSize(2);
    }

    @Test
    @DisplayName("판매자 답변과 작성자 댓글이 있을 때 작성자 댓글을 삭제하면 COMPLETED를 유지한다")
    void deleteAuthorReply_keepsCompletedStatus_test() {
        // given
        Long authorId = 1L;

        Long boardId = boardService.createBoard(
            createBoardRequest("문의 제목", "문의 내용", true),
            authorId
        );

        Long sellerReplyId = boardService.createReply(
            boardId,
            createReplyRequest("판매자 답변"),
            sellerId
        );

        Long authorReplyId = boardService.createReply(
            boardId,
            createReplyRequest("추가 문의"),
            authorId
        );

        // 판매자가 다시 답변
        boardService.createReply(
            boardId,
            createReplyRequest("판매자 추가 답변"),
            sellerId
        );

        // when
        boardService.deleteReply(authorReplyId, authorId);

        em.flush();
        em.clear();

        // then
        Board board = boardRepository.findById(boardId)
            .orElseThrow();

        assertThat(replyRepository.findById(authorReplyId)).isEmpty();
        assertThat(replyRepository.findById(sellerReplyId)).isPresent();
        assertThat(board.getStatus()).isEqualTo(BoardStatus.COMPLETED);
        assertThat(board.getReplies()).hasSize(2);
    }

    @Test
    @DisplayName("고객센터 문의 작성자는 자신의 게시글을 조회할 수 있다")
    void getCustomerService_author_test() {
        // given
        Long authorId = 1L;

        Long boardId = boardService.createBoard(
            createCustomerServiceRequest(
                "고객센터 문의",
                "문의 내용",
                true
            ),
            authorId
        );

        em.flush();
        em.clear();

        // when
        BoardResponseDto response = boardService.getBoard(boardId, authorId);

        // then
        assertThat(response.getTitle()).isEqualTo("고객센터 문의");
        assertThat(response.getContent()).isEqualTo("문의 내용");
    }

    @Test
    @DisplayName("관리자는 고객센터 비밀 문의를 조회할 수 있다")
    void getCustomerService_admin_test() {
        // given
        Long authorId = 1L;

        Long boardId = boardService.createBoard(
            createCustomerServiceRequest(
                "고객센터 비밀 문의",
                "관리자 확인이 필요한 내용",
                true
            ),
            authorId
        );

        em.flush();
        em.clear();

        loginAdmin();

        // when
        BoardResponseDto response = boardService.getBoard(boardId, 99L);

        // then
        assertThat(response.getTitle()).isEqualTo("고객센터 비밀 문의");
        assertThat(response.getContent()).isEqualTo("관리자 확인이 필요한 내용");
    }

    @Test
    @DisplayName("다른 일반 사용자는 고객센터 문의를 조회할 수 없다")
    void getCustomerService_otherUser_fail() {
        // given
        Long authorId = 1L;
        Long otherUserId = 3L;

        Long boardId = boardService.createBoard(
            createCustomerServiceRequest(
                "고객센터 비밀 문의",
                "비밀 문의 내용",
                true
            ),
            authorId
        );

        em.flush();
        em.clear();

        // when & then
        assertThatThrownBy(() ->
            boardService.getBoard(boardId, otherUserId)
        ).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("고객센터 문의는 작성자와 관리자만 댓글을 작성할 수 있다")
    void createReply_customerService_authorization_test() {
        // given
        Long authorId = 1L;
        Long otherUserId = 3L;

        Long boardId = boardService.createBoard(
            createCustomerServiceRequest(
                "고객센터 문의",
                "문의 내용",
                true
            ),
            authorId
        );

        // 다른 일반 사용자는 댓글 작성 불가
        assertThatThrownBy(() ->
            boardService.createReply(
                boardId,
                createReplyRequest("일반 사용자 댓글"),
                otherUserId
            )
        ).isInstanceOf(ForbiddenException.class);

        // 작성자는 댓글 작성 가능
        Long authorReplyId = boardService.createReply(
            boardId,
            createReplyRequest("작성자 추가 문의"),
            authorId
        );

        // 관리자는 댓글 작성 가능
        loginAdmin();

        Long adminReplyId = boardService.createReply(
            boardId,
            createReplyRequest("관리자 답변"),
            99L
        );

        em.flush();
        em.clear();

        // then
        Board board = boardRepository.findById(boardId)
            .orElseThrow();

        assertThat(replyRepository.findById(authorReplyId)).isPresent();
        assertThat(replyRepository.findById(adminReplyId)).isPresent();
        assertThat(board.getReplies()).hasSize(2);
        assertThat(board.getStatus()).isEqualTo(BoardStatus.COMPLETED);
    }


    @Test
    @DisplayName("LEGACY 게시글은 상세 조회할 수 없다")
    void getLegacyBoard_fail() {
        Long authorId = 1L;
        Board board = createLegacyBoard(authorId);

        em.flush();
        em.clear();

        assertThatThrownBy(() ->
            boardService.getBoard(board.getId(), authorId)
        ).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("LEGACY 게시판 목록은 조회할 수 없다")
    void getLegacyBoards_fail() {
        assertThatThrownBy(() ->
            boardService.getBoards(
                BoardType.LEGACY,
                null,
                PageRequest.of(0, 10),
                1L
            )
        ).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("LEGACY 게시글은 신규 작성할 수 없다")
    void createLegacyBoard_fail() {
        BoardRequestDto request = BoardRequestDto.builder()
            .title("LEGACY 신규 게시글")
            .content("신규 작성 시도")
            .boardType(BoardType.LEGACY)
            .itemId(null)
            .isSecret(false)
            .build();

        assertThatThrownBy(() ->
            boardService.createBoard(request, 1L)
        ).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("LEGACY 게시글은 수정할 수 없다")
    void updateLegacyBoard_fail() {
        Board board = createLegacyBoard(1L);

        em.flush();
        em.clear();

        assertThatThrownBy(() ->
            boardService.updateBoard(
                board.getId(),
                createBoardUpdateRequest(
                    "수정된 제목",
                    "수정된 내용",
                    false
                ),
                1L
            )
        ).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("LEGACY 게시글은 삭제할 수 없다")
    void deleteLegacyBoard_fail() {
        Board board = createLegacyBoard(1L);
        Long boardId = board.getId();

        em.flush();
        em.clear();

        assertThatThrownBy(() ->
            boardService.deleteBoard(boardId, 1L)
        ).isInstanceOf(NotFoundException.class);

        // 예외 발생 후에도 기존 게시글은 보존되어야 한다.
        assertThat(boardRepository.findById(boardId)).isPresent();
    }

    @Test
    @DisplayName("LEGACY 게시글에는 댓글을 작성할 수 없다")
    void createReplyLegacyBoard_fail() {
        Board board = createLegacyBoard(1L);

        em.flush();
        em.clear();

        assertThatThrownBy(() ->
            boardService.createReply(
                board.getId(),
                createReplyRequest("LEGACY 댓글 작성 시도"),
                1L
            )
        ).isInstanceOf(NotFoundException.class);
    }
    private Board createBoardForListTest(
        String title,
        String content,
        Long authorId,
        BoardType boardType,
        Long targetItemId,
        Boolean isSecret
    ) {
        Board board = Board.builder()
            .title(title)
            .content(content)
            .authorId(authorId)
            .boardType(boardType)
            .itemId(targetItemId)
            .isSecret(isSecret)
            .build();

        return boardRepository.save(board);
    }

    @Test
    @DisplayName("비로그인 사용자도 커뮤니티와 상품 리뷰 게시글을 조회할 수 있다")
    void getPublicBoards_guest_test() {
        Board communityBoard = createBoardForListTest(
            "커뮤니티 게시글",
            "커뮤니티 내용",
            1L,
            BoardType.COMMUNITY,
            null,
            false
        );

        Board reviewBoard = createBoardForListTest(
            "상품 리뷰 게시글",
            "상품 리뷰 내용",
            1L,
            BoardType.PRODUCT_REVIEW,
            itemId,
            false
        );

        em.flush();
        em.clear();

        Page<BoardResponseDto> communityResult = boardService.getBoards(
            BoardType.COMMUNITY,
            null,
            PageRequest.of(0, 10),
            null
        );

        Page<BoardResponseDto> reviewResult = boardService.getBoards(
            BoardType.PRODUCT_REVIEW,
            null,
            PageRequest.of(0, 10),
            null
        );

        assertThat(communityResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(communityBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("커뮤니티 게시글");
                assertThat(dto.getContent()).isEqualTo("커뮤니티 내용");
            });

        assertThat(reviewResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(reviewBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("상품 리뷰 게시글");
                assertThat(dto.getContent()).isEqualTo("상품 리뷰 내용");
            });
    }

    @Test
    @DisplayName("상품 문의 목록은 작성자와 판매자에게만 내용을 공개한다")
    void getProductInquiryBoards_authorization_test() {
        Long authorId = 1L;
        Long otherUserId = 3L;

        Board inquiryBoard = createBoardForListTest(
            "비밀 상품 문의",
            "상품 문의 내용",
            authorId,
            BoardType.PRODUCT_INQUIRY,
            itemId,
            true
        );

        em.flush();
        em.clear();

        // 작성자 조회
        Page<BoardResponseDto> authorResult = boardService.getBoards(
            BoardType.PRODUCT_INQUIRY,
            null,
            PageRequest.of(0, 10),
            authorId
        );

        // 판매자 조회
        Page<BoardResponseDto> sellerResult = boardService.getBoards(
            BoardType.PRODUCT_INQUIRY,
            null,
            PageRequest.of(0, 10),
            sellerId
        );

        // 다른 일반 사용자 조회
        Page<BoardResponseDto> otherUserResult = boardService.getBoards(
            BoardType.PRODUCT_INQUIRY,
            null,
            PageRequest.of(0, 10),
            otherUserId
        );

        // 작성자는 정상적으로 확인 가능
        assertThat(authorResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(inquiryBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("비밀 상품 문의");
                assertThat(dto.getContent()).isEqualTo("상품 문의 내용");
            });

        // 판매자도 정상적으로 확인 가능
        assertThat(sellerResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(inquiryBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("비밀 상품 문의");
                assertThat(dto.getContent()).isEqualTo("상품 문의 내용");
            });

        // 권한이 없는 사용자는 민감 정보가 마스킹됨
        assertThat(otherUserResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(inquiryBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("비밀글입니다.");
                assertThat(dto.getContent()).isNull();
            });
    }
    @Test
    @DisplayName("고객센터 목록은 작성자와 관리자에게만 내용을 공개한다")
    void getCustomerServiceBoards_authorization_test() {
        Long authorId = 1L;
        Long otherUserId = 3L;

        Board board = createBoardForListTest(
            "비밀 고객센터 문의",
            "고객센터 문의 내용",
            authorId,
            BoardType.CUSTOMER_SERVICE,
            null,
            true
        );

        em.flush();
        em.clear();

        // 작성자 조회
        Page<BoardResponseDto> authorResult = boardService.getBoards(
            BoardType.CUSTOMER_SERVICE,
            null,
            PageRequest.of(0, 10),
            authorId
        );

        // 다른 일반 사용자 조회
        Page<BoardResponseDto> otherUserResult = boardService.getBoards(
            BoardType.CUSTOMER_SERVICE,
            null,
            PageRequest.of(0, 10),
            otherUserId
        );

        // 관리자 조회
        loginAdmin();

        Page<BoardResponseDto> adminResult = boardService.getBoards(
            BoardType.CUSTOMER_SERVICE,
            null,
            PageRequest.of(0, 10),
            99L
        );

        // 작성자는 정상 조회
        assertThat(authorResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(board.getId());
                assertThat(dto.getTitle()).isEqualTo("비밀 고객센터 문의");
                assertThat(dto.getContent()).isEqualTo("고객센터 문의 내용");
            });

        // 다른 일반 사용자는 마스킹
        assertThat(otherUserResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(board.getId());
                assertThat(dto.getTitle()).isEqualTo("비밀글입니다.");
                assertThat(dto.getContent()).isNull();
            });

        // 관리자는 정상 조회
        assertThat(adminResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(board.getId());
                assertThat(dto.getTitle()).isEqualTo("비밀 고객센터 문의");
                assertThat(dto.getContent()).isEqualTo("고객센터 문의 내용");
            });
    }

    @Test
    @DisplayName("상품 문의 공개글도 권한이 없는 사용자에게는 마스킹된다")
    void getPublicProductInquiryBoards_otherUser_test() {
        Long authorId = 1L;
        Long otherUserId = 3L;

        Board board = createBoardForListTest(
            "공개 상품 문의",
            "공개 상품 문의 내용",
            authorId,
            BoardType.PRODUCT_INQUIRY,
            itemId,
            false
        );

        em.flush();
        em.clear();

        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.PRODUCT_INQUIRY,
            null,
            PageRequest.of(0, 10),
            otherUserId
        );

        assertThat(result.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(board.getId());
                assertThat(dto.getTitle()).isEqualTo("비밀글입니다.");
                assertThat(dto.getContent()).isNull();
            });
    }

    @Test
    @DisplayName("고객센터 공개글도 권한이 없는 사용자에게는 마스킹된다")
    void getPublicCustomerServiceBoards_otherUser_test() {
        Long authorId = 1L;
        Long otherUserId = 3L;

        Board board = createBoardForListTest(
            "공개 고객센터 문의",
            "공개 고객센터 문의 내용",
            authorId,
            BoardType.CUSTOMER_SERVICE,
            null,
            false
        );

        em.flush();
        em.clear();

        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.CUSTOMER_SERVICE,
            null,
            PageRequest.of(0, 10),
            otherUserId
        );

        assertThat(result.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(board.getId());
                assertThat(dto.getTitle()).isEqualTo("비밀글입니다.");
                assertThat(dto.getContent()).isNull();
            });
    }
    @Test
    @DisplayName("비로그인 사용자는 상품 문의와 고객센터 게시글 내용을 볼 수 없다")
    void getPrivateBoards_guest_test() {
        Long authorId = 1L;

        Board inquiryBoard = createBoardForListTest(
            "상품 문의",
            "상품 문의 내용",
            authorId,
            BoardType.PRODUCT_INQUIRY,
            itemId,
            false
        );

        Board customerServiceBoard = createBoardForListTest(
            "고객센터 문의",
            "고객센터 문의 내용",
            authorId,
            BoardType.CUSTOMER_SERVICE,
            null,
            false
        );

        em.flush();
        em.clear();

        Page<BoardResponseDto> inquiryResult = boardService.getBoards(
            BoardType.PRODUCT_INQUIRY,
            null,
            PageRequest.of(0, 10),
            null
        );

        Page<BoardResponseDto> customerServiceResult = boardService.getBoards(
            BoardType.CUSTOMER_SERVICE,
            null,
            PageRequest.of(0, 10),
            null
        );

        assertThat(inquiryResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(inquiryBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("비밀글입니다.");
                assertThat(dto.getContent()).isNull();
            });

        assertThat(customerServiceResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(customerServiceBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("비밀글입니다.");
                assertThat(dto.getContent()).isNull();
            });
    }


    @Test
    @DisplayName("다른 판매자는 해당 상품의 문의 내용을 조회할 수 없다")
    void getProductInquiryBoards_otherSeller_test() {
        Long authorId = 1L;
        Long otherSellerId = 4L;

        // 기존 상품과 다른 판매자가 등록한 상품 생성
        Item otherSellerItem = itemRepository.save(
            Item.builder()
                .sellerUserId(otherSellerId)
                .category(itemRepository.findById(itemId)
                    .orElseThrow()
                    .getCategory())
                .title("다른 판매자의 상품")
                .description("다른 판매자의 상품 설명")
                .price(20000)
                .saleType(SaleType.NORMAL)
                .status(ItemStatus.ON_SALE)
                .build()
        );

        Board inquiryBoard = createBoardForListTest(
            "상품 문의",
            "상품 문의 내용",
            authorId,
            BoardType.PRODUCT_INQUIRY,
            otherSellerItem.getId(),
            false
        );

        em.flush();
        em.clear();

        // 기존 상품 판매자가 다른 판매자의 상품 문의 조회
        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.PRODUCT_INQUIRY,
            null,
            PageRequest.of(0, 10),
            sellerId
        );

        assertThat(result.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(inquiryBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("비밀글입니다.");
                assertThat(dto.getContent()).isNull();
            });
    }

    @Test
    @DisplayName("상품 문의 검색 결과도 권한이 없으면 게시글 내용이 마스킹된다")
    void searchProductInquiryBoards_otherUser_test() {
        Long authorId = 1L;
        Long otherUserId = 3L;

        Board inquiryBoard = createBoardForListTest(
            "배송 일정 문의",
            "상품 배송 일정을 알려주세요",
            authorId,
            BoardType.PRODUCT_INQUIRY,
            itemId,
            true
        );

        em.flush();
        em.clear();

        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.PRODUCT_INQUIRY,
            "배송 일정",
            PageRequest.of(0, 10),
            otherUserId
        );

        assertThat(result.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(inquiryBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("비밀글입니다.");
                assertThat(dto.getContent()).isNull();
            });
    }
    @Test
    @DisplayName("상품 문의 작성자는 검색 결과에서 자신의 게시글 내용을 확인할 수 있다")
    void searchProductInquiryBoards_author_test() {
        Long authorId = 1L;

        Board inquiryBoard = createBoardForListTest(
            "배송 일정 문의",
            "상품 배송 일정을 알려주세요",
            authorId,
            BoardType.PRODUCT_INQUIRY,
            itemId,
            true
        );

        em.flush();
        em.clear();

        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.PRODUCT_INQUIRY,
            "배송 일정",
            PageRequest.of(0, 10),
            authorId
        );

        assertThat(result.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(inquiryBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("배송 일정 문의");
                assertThat(dto.getContent()).isEqualTo("상품 배송 일정을 알려주세요");
            });
    }

    @Test
    @DisplayName("고객센터 검색 결과는 권한이 없으면 마스킹된다")
    void searchCustomerServiceBoards_otherUser_test() {
        Long authorId = 1L;
        Long otherUserId = 3L;

        Board board = createBoardForListTest(
            "환불 처리 문의",
            "환불 처리 일정을 확인하고 싶습니다",
            authorId,
            BoardType.CUSTOMER_SERVICE,
            null,
            true
        );

        em.flush();
        em.clear();

        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.CUSTOMER_SERVICE,
            "환불 처리",
            PageRequest.of(0, 10),
            otherUserId
        );

        assertThat(result.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(board.getId());
                assertThat(dto.getTitle()).isEqualTo("비밀글입니다.");
                assertThat(dto.getContent()).isNull();
            });
    }
    @Test
    @DisplayName("고객센터 작성자는 검색 결과에서 자신의 문의 내용을 확인할 수 있다")
    void searchCustomerServiceBoards_author_test() {
        Long authorId = 1L;

        Board board = createBoardForListTest(
            "환불 처리 문의",
            "환불 처리 일정을 확인하고 싶습니다",
            authorId,
            BoardType.CUSTOMER_SERVICE,
            null,
            true
        );

        em.flush();
        em.clear();

        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.CUSTOMER_SERVICE,
            "환불 처리",
            PageRequest.of(0, 10),
            authorId
        );

        assertThat(result.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(board.getId());
                assertThat(dto.getTitle()).isEqualTo("환불 처리 문의");
                assertThat(dto.getContent())
                    .isEqualTo("환불 처리 일정을 확인하고 싶습니다");
            });
    }

    @Test
    @DisplayName("여러 상품 문의 목록은 게시글마다 조회 권한을 개별 적용한다")
    void getProductInquiryBoards_mixedAuthorization_test() {
        Long authorId = 1L;
        Long otherAuthorId = 3L;

        // 첫 번째 게시글: 작성자는 1번 사용자
        Board firstBoard = createBoardForListTest(
            "첫 번째 상품 문의",
            "첫 번째 문의 내용",
            authorId,
            BoardType.PRODUCT_INQUIRY,
            itemId,
            true
        );

        // 두 번째 게시글: 작성자는 3번 사용자
        Board secondBoard = createBoardForListTest(
            "두 번째 상품 문의",
            "두 번째 문의 내용",
            otherAuthorId,
            BoardType.PRODUCT_INQUIRY,
            itemId,
            false
        );

        em.flush();
        em.clear();

        // 첫 번째 작성자 조회
        Page<BoardResponseDto> authorResult = boardService.getBoards(
            BoardType.PRODUCT_INQUIRY,
            null,
            PageRequest.of(0, 10),
            authorId
        );

        // 두 번째 작성자 조회
        Page<BoardResponseDto> otherAuthorResult = boardService.getBoards(
            BoardType.PRODUCT_INQUIRY,
            null,
            PageRequest.of(0, 10),
            otherAuthorId
        );

        // 상품 판매자 조회
        Page<BoardResponseDto> sellerResult = boardService.getBoards(
            BoardType.PRODUCT_INQUIRY,
            null,
            PageRequest.of(0, 10),
            sellerId
        );

        // 첫 번째 작성자: 첫 번째 게시글 정상, 두 번째 게시글 마스킹
        assertThat(authorResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(firstBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("첫 번째 상품 문의");
                assertThat(dto.getContent()).isEqualTo("첫 번째 문의 내용");
            });

        assertThat(authorResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(secondBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("비밀글입니다.");
                assertThat(dto.getContent()).isNull();
            });

        // 두 번째 작성자: 두 번째 게시글 정상, 첫 번째 게시글 마스킹
        assertThat(otherAuthorResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(secondBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("두 번째 상품 문의");
                assertThat(dto.getContent()).isEqualTo("두 번째 문의 내용");
            });

        assertThat(otherAuthorResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(firstBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("비밀글입니다.");
                assertThat(dto.getContent()).isNull();
            });

        // 상품 판매자는 두 게시글 모두 정상 조회
        assertThat(sellerResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(firstBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("첫 번째 상품 문의");
            });

        assertThat(sellerResult.getContent())
            .anySatisfy(dto -> {
                assertThat(dto.getId()).isEqualTo(secondBoard.getId());
                assertThat(dto.getTitle()).isEqualTo("두 번째 상품 문의");
            });
    }
    @Test
    @DisplayName("권한이 없는 게시글도 마스킹된 상태로 페이징 결과에 포함된다")
    void getProductInquiryBoards_paging_test() {
        Long authorId = 1L;
        Long otherAuthorId = 3L;

        createBoardForListTest(
            "첫 번째 문의",
            "첫 번째 문의 내용",
            authorId,
            BoardType.PRODUCT_INQUIRY,
            itemId,
            true
        );

        createBoardForListTest(
            "두 번째 문의",
            "두 번째 문의 내용",
            otherAuthorId,
            BoardType.PRODUCT_INQUIRY,
            itemId,
            false
        );

        createBoardForListTest(
            "세 번째 문의",
            "세 번째 문의 내용",
            authorId,
            BoardType.PRODUCT_INQUIRY,
            itemId,
            false
        );

        em.flush();
        em.clear();

        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.PRODUCT_INQUIRY,
            null,
            PageRequest.of(0, 10),
            authorId
        );

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getNumber()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getContent()).hasSize(3);

        assertThat(result.getContent())
            .filteredOn(dto -> "비밀글입니다.".equals(dto.getTitle()))
            .hasSize(1);
    }
    @Test
    @DisplayName("게시글 목록은 ID 내림차순으로 조회된다")
    void getBoards_sortDesc_test() {
        Long authorId = 1L;

        Board firstBoard = createBoardForListTest(
            "첫 번째 게시글", "첫 번째 내용",
            authorId, BoardType.COMMUNITY, null, false
        );

        Board secondBoard = createBoardForListTest(
            "두 번째 게시글", "두 번째 내용",
            authorId, BoardType.COMMUNITY, null, false
        );

        Board thirdBoard = createBoardForListTest(
            "세 번째 게시글", "세 번째 내용",
            authorId, BoardType.COMMUNITY, null, false
        );

        em.flush();
        em.clear();

        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.COMMUNITY,
            null,
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id")),
            null
        );

        assertThat(result.getContent())
            .extracting(BoardResponseDto::getId)
            .containsExactly(
                thirdBoard.getId(),
                secondBoard.getId(),
                firstBoard.getId()
            );
    }
    @Test
    @DisplayName("게시글 목록은 ID 오름차순으로 조회된다")
    void getBoards_sortAsc_test() {
        Long authorId = 1L;

        Board firstBoard = createBoardForListTest(
            "첫 번째 게시글", "첫 번째 내용",
            authorId, BoardType.COMMUNITY, null, false
        );

        Board secondBoard = createBoardForListTest(
            "두 번째 게시글", "두 번째 내용",
            authorId, BoardType.COMMUNITY, null, false
        );

        Board thirdBoard = createBoardForListTest(
            "세 번째 게시글", "세 번째 내용",
            authorId, BoardType.COMMUNITY, null, false
        );

        em.flush();
        em.clear();

        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.COMMUNITY,
            null,
            PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "id")),
            null
        );

        assertThat(result.getContent())
            .extracting(BoardResponseDto::getId)
            .containsExactly(
                firstBoard.getId(),
                secondBoard.getId(),
                thirdBoard.getId()
            );
    }
    @Test
    @DisplayName("검색 조건과 ID 내림차순 정렬을 함께 적용한다")
    void getBoards_searchAndSort_test() {
        Long authorId = 1L;

        Board firstBoard = createBoardForListTest(
            "배송 문의 첫 번째",
            "배송 관련 내용",
            authorId,
            BoardType.COMMUNITY,
            null,
            false
        );

        createBoardForListTest(
            "환불 문의",
            "환불 관련 내용",
            authorId,
            BoardType.COMMUNITY,
            null,
            false
        );

        Board secondBoard = createBoardForListTest(
            "배송 문의 두 번째",
            "배송 관련 추가 내용",
            authorId,
            BoardType.COMMUNITY,
            null,
            false
        );

        em.flush();
        em.clear();

        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.COMMUNITY,
            "배송",
            PageRequest.of(
                0,
                10,
                Sort.by(Sort.Direction.DESC, "id")
            ),
            null
        );

        assertThat(result.getTotalElements()).isEqualTo(2);

        assertThat(result.getContent())
            .extracting(BoardResponseDto::getId)
            .containsExactly(
                secondBoard.getId(),
                firstBoard.getId()
            );
    }

    @Test
    @DisplayName("권한별 마스킹을 적용해도 게시글 ID 내림차순 정렬은 유지된다")
    void getBoards_authorizationAndSort_test() {

        // 실제 사용자 생성
        User author = createUser("author");
        User otherAuthor = createUser("otherAuthor");

        Long authorId = author.getId();
        Long otherAuthorId = otherAuthor.getId();

        // 게시글 3개 생성
        Board firstBoard = createBoardForListTest(
            "첫 번째 배송 문의", "첫 번째 배송 내용",
            authorId, BoardType.PRODUCT_INQUIRY, itemId, true
        );

        Board secondBoard = createBoardForListTest(
            "두 번째 배송 문의", "두 번째 배송 내용",
            otherAuthorId, BoardType.PRODUCT_INQUIRY, itemId, false
        );

        Board thirdBoard = createBoardForListTest(
            "세 번째 배송 문의", "세 번째 배송 내용",
            authorId, BoardType.PRODUCT_INQUIRY, itemId, false
        );

        em.flush();
        em.clear();

        Page<BoardResponseDto> result = boardService.getBoards(
            BoardType.PRODUCT_INQUIRY,
            "배송",
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id")),
            authorId
        );

        // 게시글 3개 검색 확인
        assertThat(result.getTotalElements()).isEqualTo(3);

        // ID 내림차순 정렬 확인
        assertThat(result.getContent())
            .extracting(BoardResponseDto::getId)
            .containsExactly(
                thirdBoard.getId(),
                secondBoard.getId(),
                firstBoard.getId()
            );

        // 작성자 본인 게시글은 내용 공개
        assertThat(result.getContent().get(0).getTitle())
            .isEqualTo("세 번째 배송 문의");

        // 다른 사용자의 게시글은 마스킹
        assertThat(result.getContent().get(1).getTitle())
            .isEqualTo("비밀글입니다.");

        // 작성자 본인 게시글은 내용 공개
        assertThat(result.getContent().get(2).getTitle())
            .isEqualTo("첫 번째 배송 문의");
    }

    private User createUser(String nickname) {
        return userRepository.save(
            User.builder()
                .email(nickname + "@test.com")
                .password("1234")
                .nickname(nickname)
                .build()
        );
    }
}
