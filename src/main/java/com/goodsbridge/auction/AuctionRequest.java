package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AuctionRequest {
    private Long itemId;
    private Integer startPrice;
    private LocalDateTime deadline;
    private Integer depositAmount;
    private String description;   // 경매 설명 (선택, 500자 이하)

    public void validate() {
        if (description != null && description.length() > 500) {
            throw new BadRequestException("경매 설명은 500자 이하로 입력해주세요.");
        }
        if (itemId == null) {
            throw new BadRequestException("상품 ID는 필수입니다.");
        }
        if (startPrice == null || startPrice <= 0) {
            throw new BadRequestException("시작가는 0보다 커야합니다.");
        }
        if (depositAmount == null || depositAmount <= 0) {
            throw new BadRequestException("보증금은 0보다 커야합니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (deadline == null || deadline.isBefore(now.plusMinutes(10))) {
            throw new BadRequestException("마감 시간은 현재 시간으로부터 최소 10분 이후여야 합니다.");
        }
        if (deadline.isAfter(now.plusDays(Auction.MAX_DURATION_DAYS))) {
            throw new BadRequestException("경매 마감은 최대 " + Auction.MAX_DURATION_DAYS + "일 이내로 설정해주세요.");
        }
        long startPriceValue = startPrice;
        long depositAmountValue = depositAmount;
        // 보증금은 최초 가격에서 10% ~ 20% 범위 내로 제한
        boolean validDeposit = depositAmountValue * 100 >= startPriceValue * 10
            && depositAmountValue * 100 <= startPriceValue * 20;
        if (!validDeposit) {
            throw new BadRequestException("보증금은 시작가의 10% 이상 20% 이하여야 합니다.");
        }
    }
}
