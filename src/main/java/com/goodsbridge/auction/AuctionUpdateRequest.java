package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 입찰 전 경매 수정: 시작가 · 마감 시간 · 보증금
@Getter
@NoArgsConstructor
public class AuctionUpdateRequest {
    private Integer startPrice;
    private LocalDateTime deadline;
    private Integer depositAmount;
    private String description;   // 경매 설명 (선택, 500자 이하)

    public void validate() {
        if (description != null && description.length() > 500) {
            throw new BadRequestException("경매 설명은 500자 이하로 입력해주세요.");
        }
        if (startPrice == null || startPrice <= 0) {
            throw new BadRequestException("시작가는 0보다 커야합니다.");
        }
        if (depositAmount == null || depositAmount <= 0) {
            throw new BadRequestException("보증금은 0보다 커야합니다.");
        }
        if (deadline == null || !deadline.isAfter(LocalDateTime.now())) {
            throw new BadRequestException("마감 시간은 미래 시간이어야 합니다.");
        }
        if (deadline.isAfter(LocalDateTime.now().plusDays(Auction.MAX_DURATION_DAYS))) {
            throw new BadRequestException("경매 마감은 최대 " + Auction.MAX_DURATION_DAYS + "일 이내로 설정해주세요.");
        }
        // 보증금은 시작가의 10% ~ 20% 범위 (경매 개설과 같은 규칙)
        long start = startPrice;
        long deposit = depositAmount;
        if (deposit * 100 < start * 10 || deposit * 100 > start * 20) {
            throw new BadRequestException("보증금은 시작가의 10% 이상 20% 이하여야 합니다.");
        }
    }
}
