package com.goodsbridge.chat;

import lombok.Getter;

import java.time.LocalDateTime;

// GET /api/chat/history 응답의 한 줄
@Getter
public class ChatLogResponse {

    private final Long id;
    private final ChatRole role;
    private final String content;
    private final LocalDateTime createdAt;

    public ChatLogResponse(ChatLog log) {
        this.id = log.getId();
        this.role = log.getRole();
        this.content = log.getContent();
        this.createdAt = log.getCreatedAt();
    }
}
