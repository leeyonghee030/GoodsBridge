package com.goodsbridge.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI의 ChatClient 설정.
 *
 * <p>[공부 포인트] ChatClient.Builder는 Spring AI가 자동으로 만들어 준다. Gemini 연결 정보(api-key, model)는
 * application.yaml의 spring.ai.google.genai 아래에 있고 키는 .env의 GEMINI_API_KEY에서 읽는다.
 */
@Configuration
public class ChatConfig {

    // 시스템 프롬프트: 모든 대화의 맨 앞에 붙는 "AI의 역할과 규칙"
    static final String SYSTEM_PROMPT = """
        당신은 굿즈 교환·거래 플랫폼 GoodsBridge의 도우미입니다. 항상 한국어로, 짧고 친절하게 답하세요.

        규칙:
        - 인사나 일반적인 질문에는 도구를 쓰지 말고 바로 답하세요.
        - 사용자가 자신의 교환 신청이나 진행 상태를 물으면 추측하지 말고 도구로 조회해서 답하세요.
        - 교환 신청 취소는 사용자가 취소를 분명히 요청했고 취소할 신청 번호가 확실할 때만 도구를 실행하세요.
          번호가 불확실하면 먼저 목록을 보여 주고 어떤 신청인지 물어보세요. 취소는 되돌릴 수 없습니다.
        - 도구가 실패하면 이유를 그대로 쉽게 설명하세요. 성공했다고 지어내지 마세요.
        - 다른 사용자의 정보나 이 플랫폼과 관계없는 요청은 정중히 거절하세요.
        - 모르는 것은 모른다고 말하세요.
        """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem(SYSTEM_PROMPT).build();
    }
}
