
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

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    // 게시글 작성
    @PostMapping("/boards")
    public ResponseEntity<Long> createBoard(
        @RequestBody @Valid BoardRequestDto requestDto,
        @AuthenticationPrincipal Long loginUserId
    ) {
        Long boardId = boardService.createBoard(
            requestDto,
            loginUserId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(boardId);
    }

    // 게시글 상세 조회
    @GetMapping("/boards/{id}")
    public ResponseEntity<BoardResponseDto> getBoard(
        @PathVariable Long id,
        @AuthenticationPrincipal Long loginUserId
    ) {
        BoardResponseDto response =
            boardService.getBoard(id, loginUserId);

        return ResponseEntity.ok(response);
    }

    // 공개 게시글 목록 조회 및 검색
    // GET /api/boards?page=0&size=10&sort=id,desc&keyword=공지
    @GetMapping("/boards")
    public ResponseEntity<Page<BoardResponseDto>> getBoards(
        @RequestParam(required = false) String keyword,
        @PageableDefault(
            size = 10,
            sort = "id",
            direction = Sort.Direction.DESC
        ) Pageable pageable,
        @AuthenticationPrincipal Long loginUserId
    ) {
        Page<BoardResponseDto> response =
            boardService.getBoards(
                keyword,
                pageable,
                loginUserId
            );

        return ResponseEntity.ok(response);
    }

    // 특정 상품의 문의 게시글 목록 조회
    // GET /api/items/100/inquiries?page=0&size=10&sort=id,desc
    @GetMapping("/items/{itemId}/inquiries")
    public ResponseEntity<Page<BoardResponseDto>> getProductInquiries(
        @PathVariable Long itemId,
        @PageableDefault(
            size = 10,
            sort = "id",
            direction = Sort.Direction.DESC
        ) Pageable pageable,
        @AuthenticationPrincipal Long loginUserId
    ) {
        Page<BoardResponseDto> response =
            boardService.getProductInquiries(
                itemId,
                pageable,
                loginUserId
            );

        return ResponseEntity.ok(response);
    }

    // 특정 상품의 문의 게시글 목록 조회: 상품 판매자 전용
    // GET /api/items/100/seller-inquiries?page=0&size=10&sort=id,desc
    @GetMapping("/items/{itemId}/seller-inquiries")
    public ResponseEntity<Page<BoardResponseDto>> getSellerProductInquiries(
        @PathVariable Long itemId,
        @PageableDefault(
            size = 10,
            sort = "id",
            direction = Sort.Direction.DESC
        ) Pageable pageable,
        @AuthenticationPrincipal Long loginUserId
    ) {
        Page<BoardResponseDto> response =
            boardService.getSellerProductInquiries(
                itemId,
                pageable,
                loginUserId
            );

        return ResponseEntity.ok(response);
    }

    // 내가 작성한 고객센터 문의 목록
    @GetMapping("/customer-inquiries")
    public ResponseEntity<Page<BoardResponseDto>> getMyCustomerInquiries(
        @PageableDefault(
            size = 10,
            sort = "id",
            direction = Sort.Direction.DESC
        ) Pageable pageable,
        @AuthenticationPrincipal Long loginUserId
    ) {
        Page<BoardResponseDto> response =
            boardService.getMyCustomerServiceInquiries(
                pageable,
                loginUserId
            );

        return ResponseEntity.ok(response);
    }

    // 관리자 전용: 전체 고객센터 문의 목록
    @GetMapping("/admin/boards/customer-service")
    public ResponseEntity<Page<BoardResponseDto>> getAllCustomerServiceInquiries(
        @PageableDefault(
            size = 10,
            sort = "id",
            direction = Sort.Direction.DESC
        ) Pageable pageable
    ) {
        Page<BoardResponseDto> response =
            boardService.getAllCustomerServiceInquiries(pageable);

        return ResponseEntity.ok(response);
    }

    // 댓글 작성
    @PostMapping("/boards/{id}/replies")
    public ResponseEntity<Long> createReply(
        @PathVariable Long id,
        @RequestBody @Valid ReplyRequestDto requestDto,
        @AuthenticationPrincipal Long loginUserId
    ) {
        Long replyId = boardService.createReply(
            id,
            requestDto,
            loginUserId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(replyId);
    }

    // 댓글 삭제
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
        @RequestBody @Valid BoardUpdateRequestDto requestDto,
        @AuthenticationPrincipal Long loginUserId
    ) {
        boardService.updateBoard(
            id,
            requestDto,
            loginUserId
        );

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
