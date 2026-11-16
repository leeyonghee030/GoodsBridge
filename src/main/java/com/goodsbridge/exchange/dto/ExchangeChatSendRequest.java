package com.goodsbridge.exchange.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

// WebSocket 으로 들어오는 채팅 메시지 (SEND /app/exchange-requests/{id}/chat)
@Getter
@NoArgsConstructor
public class ExchangeChatSendRequest {

    private String content;
}
