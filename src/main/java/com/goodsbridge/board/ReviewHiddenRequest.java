package com.goodsbridge.board;

import jakarta.validation.constraints.NotNull;

/** 판매자가 리뷰를 비공개로 바꾸거나(true) 다시 공개한다(false) */
public record ReviewHiddenRequest(@NotNull(message = "hidden 값은 필수입니다.") Boolean hidden) {
}
