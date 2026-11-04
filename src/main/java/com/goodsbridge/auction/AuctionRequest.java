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

    public void validate() {
        if (itemId == null) {
            throw new BadRequestException("상품 ID는 필수입니다.");
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
        long startPriceValue = startPrice;
        long depositAmountValue = depositAmount;
        // 보증금과 최초 가격간 관계
        boolean validDeposit = depositAmountValue * 100 >= startPriceValue * 10
            && depositAmountValue * 100 <= startPriceValue * 20;
        if (!validDeposit) {
            throw new BadRequestException("보증금은 시작가의 10% 이상 20% 이하여야 합니다.");
        }
    }
}
