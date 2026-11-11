package com.goodsbridge.exchange.dto;

import com.goodsbridge.exchange.ExchangeRequest;
import com.goodsbridge.exchange.ExchangeRequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 가게 사장님용 교환 목록/상세 (교환번호로 찾아서 누가 왔는지 체크)
@Getter
@Builder
public class ShopExchangeResponse {

    private Long id;                 // 교환번호 (사용자 화면에도 같은 번호가 보인다)
    private Long shopId;
    private String shopName;
    private ExchangeRequestStatus status;

    private String userANickname;    // 교환글 주인(A)
    private String userBNickname;    // 제안자(B)
    private String itemTitle;        // A의 상품
    private String itemImageUrl;
    private String itemBTitle;       // B의 상품
    private String itemBImageUrl;

    private Integer depositAmountA;
    private Integer depositAmountB;
    private Boolean userAShowed;
    private Boolean userBShowed;

    private LocalDateTime meetingAt;
    private Boolean meetingConfirmed;
    private LocalDateTime deadline;  // 방문 기한
    private LocalDateTime bothCheckedAt; // 둘 다 체크한 시각 (이 시각 + 5분에 정산)

    public static ShopExchangeResponse from(ExchangeRequest e, String userBNickname) {
        return ShopExchangeResponse.builder()
            .id(e.getId())
            .shopId(e.getShop().getId())
            .shopName(e.getShop().getName())
            .status(e.getStatus())
            .userANickname(e.getExchangeItemA().getUser().getNickname())
            .userBNickname(userBNickname)
            .itemTitle(e.getExchangeItemA().getItem().getTitle())
            .itemImageUrl(e.getExchangeItemA().getItem().getImageUrl())
            .itemBTitle(e.getItemB().getTitle())
            .itemBImageUrl(e.getItemB().getImageUrl())
            .depositAmountA(e.getDepositAmountA())
            .depositAmountB(e.getDepositAmountB())
            .userAShowed(e.getUserAShowed())
            .userBShowed(e.getUserBShowed())
            .meetingAt(e.getMeetingAt())
            .meetingConfirmed(e.isMeetingConfirmed())
            .deadline(e.getDeadline())
            .bothCheckedAt(e.getBothCheckedAt())
            .build();
    }
}
