package com.goodsbridge.board;

import com.goodsbridge._core.exception.ErrorCode;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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

        // 비밀글인 경우 작성자 본인만 조회 가능 (관리자 권한이 있다면 관리자 예외 처리 추가 가능)
        if (board.getIsSecret()) {
            if (loginUserId == null || !board.getAuthorId().equals(loginUserId)) {
                throw new ForbiddenException("게시글 ㅎ권한이 없습니다.");
            }
        }

        return BoardResponseDto.from(board);
    }

    public List<BoardResponseDto> getBoards() {
        return boardRepository.findAll().stream()
            .map(BoardResponseDto::from)
            .collect(Collectors.toList());
    }

    public BoardResponseDto getBoard(Long id) {
        // 페치 조인을 통해 게시글과 댓글을 한 번에 조회 (N+1 문제 해결)
        Board board = boardRepository.findByIdWithReplies(id)
            .orElseThrow(() -> new NotFoundException("게시글을 찾을수 없습니다."));

        return BoardResponseDto.from(board);
    }

    @Transactional
    public Long createReply(Long boardId, ReplyRequestDto requestDto, Long loginUserId) {
        // 1. 게시글 조회
        Board board = boardRepository.findById(boardId)
            .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));

        // 2. 댓글 생성 및 저장
        Reply reply = Reply.builder()
            .board(board)
            .content(requestDto.getContent())
            .authorId(loginUserId)
            .build();

        Long replyId = replyRepository.save(reply).getId();

        // 3. 댓글이 작성되면 게시판 상태를 '답변 완료'로 자동 변경
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
            .orElseThrow(() -> new NotFoundException("게시글을 찾을 없습니다."));

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
    public Page<BoardResponseDto> getBoards(String keyword, Pageable pageable) {
        Page<Board> boardPage;

        if (keyword == null || keyword.trim().isEmpty()) {
            boardPage = boardRepository.findAll(pageable);
        } else {
            boardPage = boardRepository.findByTitleContainingOrContentContaining(keyword, keyword, pageable);
        }

        return boardPage.map(BoardResponseDto::from);
    }
}
