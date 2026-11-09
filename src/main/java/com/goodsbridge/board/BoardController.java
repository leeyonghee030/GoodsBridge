package com.goodsbridge.board;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @PostMapping("/boards")
    public ResponseEntity<Long> createBoard(
        @RequestBody @Valid BoardRequestDto requestDto,
        @AuthenticationPrincipal Long loginUserId
    ) {
        Long boardId = boardService.createBoard(requestDto, loginUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(boardId);
    }

    // 게시글 목록 조회 (페이징 및 검색 지원)
    // 요청 예시: GET /api/boards?page=0&size=10&sort=id,desc&keyword=공지
    @GetMapping("/boards")
    public ResponseEntity<Page<BoardResponseDto>> getBoards(
        @RequestParam(required = false) String keyword,
        @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<BoardResponseDto> response = boardService.getBoards(keyword, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/boards/{id}")
    public ResponseEntity<BoardResponseDto> getBoard(
        @PathVariable Long id,
        @AuthenticationPrincipal Long loginUserId
    ) {
        BoardResponseDto response = boardService.getBoard(id, loginUserId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/boards/{id}/replies")
    public ResponseEntity<Long> createReply(
        @PathVariable Long id,
        @RequestBody @Valid ReplyRequestDto requestDto,
        @AuthenticationPrincipal Long loginUserId
    ) {
        Long replyId = boardService.createReply(id, requestDto, loginUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(replyId);
    }

    @DeleteMapping("/replies/{id}")
    public ResponseEntity<Void> deleteReply(
        @PathVariable Long id,
        @AuthenticationPrincipal Long loginUserId
    ) {
        boardService.deleteReply(id, loginUserId);
        return ResponseEntity.noContent().build();
    }

    // 게시글 수정
    @PutMapping("/boards/{id}")
    public ResponseEntity<Void> updateBoard(
        @PathVariable Long id,
        @RequestBody @Valid BoardRequestDto requestDto,
        @AuthenticationPrincipal Long loginUserId
    ) {
        boardService.updateBoard(id, requestDto, loginUserId);
        return ResponseEntity.ok().build();
    }

    // 게시글 삭제
    @DeleteMapping("/boards/{id}")
    public ResponseEntity<Void> deleteBoard(
        @PathVariable Long id,
        @AuthenticationPrincipal Long loginUserId
    ) {
        boardService.deleteBoard(id, loginUserId);
        return ResponseEntity.noContent().build();
    }

}
