package com.goodsbridge.event.dto;


import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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
    private Integer maxPerUser;   // 1인당 최대 구매 수량 (안 보내면 1개)
    private LocalDateTime deadline;
    private LocalDate visitUntil;   // 손님 방문 마감일 (안 보내면 모집 마감일 + 7일)

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
            throw new BadRequestException("모집 수량은 1-100개 사이 값이여야 합니다.");
        }
        if (feeAmount == null || feeAmount < 5000 || feeAmount > 100000) {
            throw new BadRequestException("참가비는 5,000 - 100,000 사이 값이여야  합니다.");
        }
        if (maxPerUser != null && (maxPerUser < 1 || maxPerUser > EventParticipationRequest.MAX_QUANTITY || maxPerUser > capacity)) {
            throw new BadRequestException("1인당 최대 구매 수량은 1-" + EventParticipationRequest.MAX_QUANTITY + "개, 모집 수량 이하로 입력해 주세요.");
        }
        // 현장 결제 금액 = 상품 가격 - 참가비 이므로 상품 가격이 참가비보다 커야 한다
        if (itemPrice <= feeAmount) {
            throw new BadRequestException("상품 가격은 참가비보다 커야 합니다.");
        }
        if (deadline == null || deadline.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("마감 시각은 현재보다 미래여야 합니다.");
        }
        if (visitUntil != null && visitUntil.isBefore(deadline.toLocalDate())) {
            throw new BadRequestException("방문 마감일은 모집 마감일 이후여야 합니다.");
        }
    }
}
