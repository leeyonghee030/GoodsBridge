package com.goodsbridge.board;

import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    private final BoardRepository boardRepository;
    private final ReplyRepository replyRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long createBoard(BoardRequestDto requestDto, Long loginUserId) {
        Board board = Board.builder()
            .title(requestDto.getTitle())
            .content(requestDto.getContent())
            .authorId(loginUserId)
            .itemId(requestDto.getItemId())
            .boardType(requestDto.getBoardType()) // 추가
            .isSecret(requestDto.getIsSecret())
            .build();
        return boardRepository.save(board).getId();
    }

    public BoardResponseDto getBoard(Long id, Long loginUserId) {
        Board board = boardRepository.findByIdWithReplies(id)
            .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));

        // 비밀글은 작성자 본인만 조회 가능
        if (Boolean.TRUE.equals(board.getIsSecret())) {
            if (loginUserId == null ||
                !board.getAuthorId().equals(loginUserId)) {
                throw new ForbiddenException("비밀글을 조회할 권한이 없습니다.");
            }
        }

        return BoardResponseDto.from(board, collectNicknames(List.of(board)));
    }

    @Transactional
    public Long createReply(
        Long boardId,
        ReplyRequestDto requestDto,
        Long loginUserId
    ) {
        Board board = boardRepository.findById(boardId)
            .orElseThrow(() ->
                new NotFoundException("게시글을 찾을 수 없습니다."));

        // 비밀글은 작성자 본인만 댓글 작성 가능
        if (Boolean.TRUE.equals(board.getIsSecret())) {
            if (loginUserId == null ||
                !Objects.equals(board.getAuthorId(), loginUserId)) {
                throw new ForbiddenException(
                    "비밀글에 댓글을 작성할 권한이 없습니다."
                );
            }
        }

        Reply reply = Reply.builder()
            .board(board)
            .content(requestDto.getContent())
            .authorId(loginUserId)
            .build();

        // 양방향 연관관계 설정
        board.addReply(reply);

        Long replyId = replyRepository.save(reply).getId();

        // 3. 댓글이 작성되면 게시판 상태를 '답변 완료'로 자동 변경
        board.complete();

        return replyId;
    }

    @Transactional
    public void deleteReply(Long replyId, Long loginUserId) {
        Reply reply = replyRepository.findById(replyId)
            .orElseThrow(() -> new NotFoundException("댓글을 찾을 수 없습니다."));

        if (!Objects.equals(reply.getAuthorId(), loginUserId)) {
            throw new ForbiddenException("댓글을 삭제할 권한이 없습니다.");
        }

        Board board = reply.getBoard();

        // 부모 엔티티 컬렉션에서도 댓글 제거
        board.removeReply(reply);

        // DB에서 댓글 삭제를 명시적으로 요청
        replyRepository.delete(reply);

        // 댓글 삭제 후 남은 댓글 개수 확인
        if (replyRepository.countByBoardId(board.getId()) == 0) {
            board.pending();
        }
    }

    @Transactional
    public void updateBoard(Long id, BoardUpdateRequestDto requestDto, Long loginUserId) {
        Board board = boardRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));

        if (!board.getAuthorId().equals(loginUserId)) {
            throw new ForbiddenException("게시글 권한이 없습니다.");
        }

        board.update(
            requestDto.getTitle(),
            requestDto.getContent(),
            requestDto.getIsSecret()
        );
    }

    @Transactional
    public void deleteBoard(Long id, Long loginUserId) {
        // 1. 게시글 존재 여부 확인
        Board board = boardRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));

        // 2. 작성자 본인 검증
        if (!board.getAuthorId().equals(loginUserId)) {
            throw new ForbiddenException("게시글 권한이 없습니다.");
        }

        // 3. 게시글 삭제 (Cascade 설정에 따라 연관된 댓글도 함께 처리 가능)
        boardRepository.delete(board);
    }

    // 페이징 및 검색 지원 목록 조회
    public Page<BoardResponseDto> getBoards(
        String keyword,
        Pageable pageable,
        Long loginUserId
    ) {
        Page<Board> boardPage;

        String searchKeyword = keyword == null ? "" : keyword.trim();

        if (searchKeyword.isEmpty()) {
            boardPage = boardRepository.findAll(pageable);
        } else {
            boardPage = boardRepository.findByKeyword(keyword, pageable);
        }

        Map<Long, String> nicknames = collectNicknames(boardPage.getContent());
        return boardPage.map(board -> BoardResponseDto.from(board, nicknames));
    }

    // 게시글(+댓글) 작성자 ID들을 모아 닉네임을 한 번에 조회
    private Map<Long, String> collectNicknames(List<Board> boards) {
        Set<Long> authorIds = new HashSet<>();
        for (Board board : boards) {
            authorIds.add(board.getAuthorId());
            for (Reply reply : board.getReplies()) {
                authorIds.add(reply.getAuthorId());
            }
        }
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(authorIds).stream()
            .collect(Collectors.toMap(User::getId, User::getNickname));
    }

    // 특정 상품의 문의 게시글 페이징 조회
    public Page<BoardResponseDto> getProductInquiries(
        Long itemId,
        Pageable pageable,
        Long loginUserId
    ) {
        Page<Board> boardPage = boardRepository.findByItemIdAndBoardType(
            itemId,
            BoardType.PRODUCT_INQUIRY,
            pageable
        );

        return boardPage.map(
            board -> BoardResponseDto.fromForList(board, loginUserId)
        );
    }
}
