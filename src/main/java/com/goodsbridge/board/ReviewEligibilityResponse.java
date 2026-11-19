package com.goodsbridge.board;

/** 이 상품에 지금 리뷰를 쓸 수 있는지: 구매확정했고 아직 리뷰를 쓰지 않은 구매가 있으면 그 구매 id */
public record ReviewEligibilityResponse(boolean reviewable, Long purchaseId) {

    public static ReviewEligibilityResponse no() {
        return new ReviewEligibilityResponse(false, null);
    }
}
