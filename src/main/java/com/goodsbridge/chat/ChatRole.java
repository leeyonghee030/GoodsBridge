package com.goodsbridge.chat;

/**
 * 대화 로그(ChatLog) 한 줄을 누가 말했는지 구분한다.
 */
public enum ChatRole {
    USER,       // 사용자가 보낸 메시지
    ASSISTANT,  // AI가 한 답변
    TOOL        // AI가 도구(@Tool)를 실행한 기록 (예: "교환 신청 12번 취소")
}
