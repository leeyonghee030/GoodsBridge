package com.goodsbridge.exchange.dto;

import com.goodsbridge.exchange.ExchangeMessage;
import lombok.Getter;

import java.time.LocalDateTime;

// 교환 채팅 메시지 응답 (REST 이력 조회, WebSocket 브로드캐스트 공용)
@Getter
public class ExchangeMessageResponse {

    private final Long id;
    private final Long senderUserId;
    private final String content;
    private final LocalDateTime createdAt;

    private ExchangeMessageResponse(ExchangeMessage message) {
        this.id = message.getId();
        this.senderUserId = message.getSenderUserId();
        this.content = message.getContent();
        this.createdAt = message.getCreatedAt();
    }

    public static ExchangeMessageResponse from(ExchangeMessage message) {
        return new ExchangeMessageResponse(message);
    }
}
