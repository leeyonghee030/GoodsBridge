
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import org.springframework.web.bind.annotation.RestController;

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

    // 게시판 유형별 게시글 목록 조회 및 검색
// GET /api/boards?boardType=COMMUNITY&page=0&size=10&sort=id,desc
// GET /api/boards?boardType=PRODUCT_REVIEW&page=0&size=10
// GET /api/boards?boardType=PRODUCT_INQUIRY&page=0&size=10
// GET /api/boards?boardType=CUSTOMER_SERVICE&page=0&size=10
    // 상품 상세 화면용 필터:
    // GET /api/boards?boardType=PRODUCT_REVIEW&itemId=3          (그 상품의 리뷰)
    // GET /api/boards?boardType=PRODUCT_INQUIRY&targetType=EVENT&targetId=5   (그 이벤트의 문의)
    @GetMapping("/boards")
    public ResponseEntity<Page<BoardResponseDto>> getBoards(
        @RequestParam(defaultValue = "COMMUNITY") BoardType boardType,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long itemId,
        @RequestParam(required = false) InquiryTargetType targetType,
        @RequestParam(required = false) Long targetId,
        @PageableDefault(
            size = 10,
            sort = "id",
            direction = Sort.Direction.DESC
        ) Pageable pageable,
        @AuthenticationPrincipal Long loginUserId
    ) {
        Page<BoardResponseDto> response =
            boardService.getBoards(
                boardType,
                keyword,
                itemId,
                targetType,
                targetId,
                pageable,
                loginUserId
            );

        return ResponseEntity.ok(response);
    }

    // 이 상품에 지금 리뷰를 쓸 수 있는지 (구매확정했고 아직 리뷰를 쓰지 않은 구매가 있는지). 비로그인은 false
    @GetMapping("/boards/review-eligibility")
    public ResponseEntity<ReviewEligibilityResponse> getReviewEligibility(
        @RequestParam Long itemId,
        @AuthenticationPrincipal Long loginUserId
    ) {
        return ResponseEntity.ok(boardService.getReviewEligibility(itemId, loginUserId));
    }

    // 내가 쓴 리뷰 (구매 id -> 게시글 id). 내 구매 내역에서 "리뷰 쓰기"/"내 리뷰" 버튼을 가르는 데 쓴다
    @GetMapping("/boards/my-reviews")
    public ResponseEntity<Map<Long, Long>> getMyReviews(
        @AuthenticationPrincipal Long loginUserId
    ) {
        return ResponseEntity.ok(boardService.getMyReviewIds(loginUserId));
    }

    // 마이페이지 "내 문의": 내가 쓴 상품 문의. targetType(ITEM/EVENT/AUCTION)으로 대상을 좁힐 수 있다
    @GetMapping("/boards/my-inquiries")
    public ResponseEntity<Page<BoardManageResponse>> getMyInquiries(
        @RequestParam(required = false) InquiryTargetType targetType,
        @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
        @AuthenticationPrincipal Long loginUserId
    ) {
        return ResponseEntity.ok(boardService.getMyInquiries(loginUserId, targetType, pageable));
    }

    // 판매자 "문의 관리": 내 상품·이벤트·경매에 온 문의
    @GetMapping("/boards/seller/inquiries")
    public ResponseEntity<Page<BoardManageResponse>> getSellerInquiries(
        @RequestParam(required = false) InquiryTargetType targetType,
        @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
        @AuthenticationPrincipal Long loginUserId
    ) {
        return ResponseEntity.ok(boardService.getSellerInquiries(loginUserId, targetType, pageable));
    }

    // 판매자 "리뷰 관리": 내 상품의 리뷰 (비공개로 바꾼 것 포함)
    @GetMapping("/boards/seller/reviews")
    public ResponseEntity<Page<BoardManageResponse>> getSellerReviews(
        @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
        @AuthenticationPrincipal Long loginUserId
    ) {
        return ResponseEntity.ok(boardService.getSellerReviews(loginUserId, pageable));
    }

    // 문의 탭의 개수 (ALL/ITEM/EVENT/AUCTION). scope=seller 면 내 상품에 온 문의, 아니면 내가 쓴 문의
    @GetMapping("/boards/inquiry-counts")
    public ResponseEntity<Map<String, Long>> getInquiryCounts(
        @RequestParam(defaultValue = "mine") String scope,
        @AuthenticationPrincipal Long loginUserId
    ) {
        return ResponseEntity.ok(boardService.getInquiryCounts(loginUserId, "seller".equals(scope)));
    }

    // 관리자 "고객센터 문의": 모든 고객센터 문의. status(PENDING/COMPLETED)로 좁힐 수 있다
    @GetMapping("/boards/admin/support")
    public ResponseEntity<Page<BoardManageResponse>> getSupportInquiries(
        @RequestParam(required = false) BoardStatus status,
        @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
        @AuthenticationPrincipal Long loginUserId
    ) {
        return ResponseEntity.ok(boardService.getSupportInquiries(loginUserId, status, pageable));
    }

    // 관리자 고객센터 탭의 개수 (ALL/PENDING/COMPLETED)
    @GetMapping("/boards/admin/support/counts")
    public ResponseEntity<Map<String, Long>> getSupportCounts(
        @AuthenticationPrincipal Long loginUserId
    ) {
        return ResponseEntity.ok(boardService.getSupportCounts(loginUserId));
    }

    // 판매자가 자기 상품의 리뷰를 비공개로 바꾸거나(true) 다시 공개한다(false)
    @PatchMapping("/boards/{id}/hidden")
    public ResponseEntity<Void> setReviewHidden(
        @PathVariable Long id,
        @RequestBody @Valid ReviewHiddenRequest request,
        @AuthenticationPrincipal Long loginUserId
    ) {
        boardService.setReviewHidden(id, request.hidden(), loginUserId);
        return ResponseEntity.noContent().build();
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
