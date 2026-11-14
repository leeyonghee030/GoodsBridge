package com.goodsbridge.exchange.dto;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 교환 게시글 수정 요청 (PATCH /api/exchange-items/{id})
@Getter
@NoArgsConstructor
public class ExchangeItemUpdateRequest {

    private String title;           // 게시글 제목 — 비우면 상품 이름이 제목으로 보인다 (상품 이름은 바뀌지 않는다)
    private String wantDescription; // 원하는 것
    private Long shopId;            // 교환장소

    public void validate() {
        if (title != null && title.length() > 100) {
            throw new BadRequestException("제목은 100자 이내여야 합니다.");
        }
        if (wantDescription == null || wantDescription.isBlank()) {
            throw new BadRequestException("원하는 것을 적어주세요.");
        }
        if (shopId == null) {
            throw new BadRequestException("가게를 지정해주세요.");
        }
    }
}
