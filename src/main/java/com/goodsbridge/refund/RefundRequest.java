package com.goodsbridge.refund;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
public class RefundRequest {
    private String paymentId;
    private Integer requestedAmount;
    private String reason;

    public void validate() {
        if (paymentId == null || paymentId.isBlank()) {
            throw new BadRequestException("paymentId가 필요합니다.");
        }
        if (requestedAmount == null || requestedAmount <= 0) {
            throw new BadRequestException("환불 요청 금액은 1 이상이어야 합니다.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("환불 사유는 필수입니다.");
        }
        if (reason.length() > 500) {
            throw new BadRequestException("환불 사유는 500자 이하로 입력해주세요.");
        }
    }
}
