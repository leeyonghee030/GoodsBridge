package com.goodsbridge.board;

import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    private final BoardRepository boardRepository;
    private final ReplyRepository replyRepository;

    @Transactional
    public Long createBoard(BoardRequestDto requestDto, Long loginUserId) {
        Board board = Board.builder()
            .title(requestDto.getTitle())
            .content(requestDto.getContent())
            .authorId(loginUserId)
            .itemId(requestDto.getItemId())
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

        return BoardResponseDto.from(board);
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
            .content(requestDto.getContent())
            .authorId(loginUserId)
            .build();

        // 양방향 연관관계 설정
        board.addReply(reply);

        Long replyId = replyRepository.save(reply).getId();

        board.complete();

        return replyId;
    }

    @Transactional
    public void deleteReply(Long replyId, Long loginUserId) {
        Reply reply = replyRepository.findById(replyId)
            .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));

        if (!reply.getAuthorId().equals(loginUserId)) {
            throw new ForbiddenException("게시글 권한이 없습니다.");
        }

        replyRepository.delete(reply);
    }

    @Transactional
    public void updateBoard(Long id, BoardRequestDto requestDto, Long loginUserId) {
        Board board = boardRepository.findById(id)
            .orElseThrow(() ->  new NotFoundException("게시글을 찾을 수 없습니다."));

        if (!board.getAuthorId().equals(loginUserId)) {
            throw new ForbiddenException("게시글 권한이 없습니다.");
        }

        board.update(requestDto.getTitle(), requestDto.getContent(), requestDto.getItemId(), requestDto.getIsSecret());
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

        if (keyword == null || keyword.trim().isEmpty()) {
            boardPage = boardRepository.findAll(pageable);
        } else {
            boardPage = boardRepository
                .findByTitleContainingOrContentContaining(
                    keyword,
                    keyword,
                    pageable
                );
        }

        return boardPage.map(
            board -> BoardResponseDto.fromForList(board, loginUserId)
        );
    }

}
