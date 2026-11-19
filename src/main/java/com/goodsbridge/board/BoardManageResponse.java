package com.goodsbridge.board;

import java.time.LocalDateTime;

/**
 * 마이페이지 "내 문의", 판매자의 "문의 관리"·"리뷰 관리" 목록의 한 줄.
 * 목록을 보는 사람(작성자 또는 해당 상품의 판매자)에게만 내려가므로 내용도 함께 담는다.
 */
public record BoardManageResponse(
    Long id,
    BoardType boardType,
    String title,
    String content,
    String authorNickname,
    Long itemId,
    String itemTitle,
    InquiryTargetType targetType,   // 문의가 남겨진 곳(상품/이벤트/경매). 리뷰는 null
    Long targetId,
    BoardStatus status,             // 문의의 답변 상태. 리뷰는 null
    boolean hiddenBySeller,         // 리뷰: 판매자가 비공개로 바꿨는지
    LocalDateTime createdAt
) {
}
