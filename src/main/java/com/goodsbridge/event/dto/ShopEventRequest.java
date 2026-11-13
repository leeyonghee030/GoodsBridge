package com.goodsbridge.event.dto;


import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 이벤트 생성 요청: 이벤트 상품을 새로 등록하면서(판매유형 EVENT) 이벤트를 연다
@Getter
@NoArgsConstructor
public class ShopEventRequest {
    // 새로 등록할 이벤트 상품
    private String itemTitle;
    private Long categoryId;
    private Integer itemPrice;
    private String itemDescription;
    private String itemImageUrl;

    // 이벤트 조건
    private Integer capacity;
    private Integer feeAmount;
    private LocalDateTime deadline;

    public void validate() {
        if (itemTitle == null || itemTitle.isBlank() || itemTitle.trim().length() > 100) {
            throw new BadRequestException("상품명은 1-100자로 입력해 주세요.");
        }
        if (categoryId == null || categoryId <= 0) {
            throw new BadRequestException("카테고리를 선택해 주세요.");
        }
        if (itemPrice == null || itemPrice <= 0) {
            throw new BadRequestException("상품 가격은 0보다 커야 합니다.");
        }
        if (capacity == null || capacity <= 0 || capacity > 100) {
            throw new BadRequestException("모집 인원은 1-100명 사이 값이여야 합니다.");
        }
        if (feeAmount == null || feeAmount < 5000 || feeAmount > 100000) {
            throw new BadRequestException("참가비는 5,000 - 100,000 사이 값이여야  합니다.");
        }
        if (deadline == null || deadline.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("마감 시각은 현재보다 미래여야 합니다.");
        }
    }
}
