package com.goodsbridge.chat;

import lombok.Getter;
import lombok.NoArgsConstructor;

// POST /api/chat 요청 본문: { "content": "내 교환 신청 보여줘" }
@Getter
@NoArgsConstructor
public class ChatRequest {

    private String content;

    public ChatRequest(String content) {
        this.content = content;
    }
}
