package com.goodsbridge.exchange.dto;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 교환 게시글 등록 요청 (POST /api/exchange-items)
@Getter
@NoArgsConstructor
public class ExchangeItemCreateRequest {

    private Long itemId;           // 내가 가진 것 (본인 소유 Item id)
    private String wantDescription; // 원하는 것 (자유 텍스트)
    private Long shopId;           // 교환장소 (이름검색으로 선택한 Shop id)

    public void validate() {
        if (itemId == null) {
            throw new BadRequestException("아이템을 등록해주세요.");
        }
        if (wantDescription == null || wantDescription.isBlank()) {
            throw new BadRequestException("원하는 것을 적어주세요.");
        }
        if (shopId == null) {
            throw new BadRequestException("가게를 지정해주세요.");
        }
    }
}
