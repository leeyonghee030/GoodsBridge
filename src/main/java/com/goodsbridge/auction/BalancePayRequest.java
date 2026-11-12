package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BalancePayRequest {
    private String paymentId;   // PortOne 결제 id (금액은 서버가 계산하므로 받지 않는다)
    private String deliveryAddress;

    // 실제 잔금 결제에서는 주소 검증을 통과해야 한다.
    public BalancePayRequest(String paymentId) {
        this(paymentId, null);
    }

    public void validateDeliveryAddress() {
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            throw new BadRequestException("배송지 주소가 필요합니다.");
        }
        if (deliveryAddress.length() > 255) {
            throw new BadRequestException("배송지 주소는 255자 이하로 입력해주세요.");
        }
    }
}
