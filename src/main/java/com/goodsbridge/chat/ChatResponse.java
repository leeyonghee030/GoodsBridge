package com.goodsbridge.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

// POST /api/chat 응답: { "reply": "AI의 답변" }
@Getter
@AllArgsConstructor
public class ChatResponse {

    private String reply;
}
