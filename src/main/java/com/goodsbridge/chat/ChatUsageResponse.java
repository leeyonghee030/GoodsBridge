package com.goodsbridge.chat;

// GET /api/chat/usage 응답: 하루 AI 질문 한도와 오늘 남은 횟수
public record ChatUsageResponse(int limit, int remaining) {
}
