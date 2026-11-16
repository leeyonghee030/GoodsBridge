package com.goodsbridge.chat;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

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
        - 사용자가 자신의 교환 신청이나 진행 상태를 물으면 추측하지 말고 getMyExchangeRequests로 조회해서 답하세요.
          기본은 처리할 일이 있는 '대기 중' 신청만 보여 주고, 먼저 분류별 건수를 한 줄로 요약해 주세요(예: 대기 중 2건 · 확정 3건 · 완료 1건 · 취소·거절·만료 4건).
          사용자가 확정·완료·취소 같은 다른 상태를 원하면 category를 바꿔 다시 조회하세요. 전체를 한꺼번에 나열하지 마세요.
          답변 끝에는 전체 내역을 볼 수 있는 링크 [전체 교환 내역](/my/exchange)를 붙여 주세요.
        - 조회처럼 읽기만 하는 요청은 바로 도구로 확인해서 답하세요.
        - 취소처럼 데이터를 바꾸는 일은 반드시 이 순서를 지키세요. 순서를 건너뛰면 취소되지 않습니다.
          1) 취소할 신청 번호가 불확실하면 먼저 목록을 보여 주고 어떤 신청인지 물어보세요.
          2) prepareCancelExchangeRequest로 취소할 수 있는지 확인하세요. 취소할 수 없으면 이유를 설명하고 끝내세요.
          3) 확인 결과를 사용자에게 쉽게 설명하세요: 어떤 신청인지, 취소하면 어떻게 되는지(상태 변경, 보증금 환불 금액, 상대에게 알림,
             되돌릴 수 없음)를 알려 주고 "정말 취소할까요?"라고 물어보세요. 이 답변에서는 취소를 실행하지 마세요.
          4) 사용자가 다음 메시지에서 "네", "취소해줘"처럼 분명히 동의하면 그때 cancelExchangeRequest를 호출하세요.
             동의가 없거나 망설이면 취소하지 말고 그대로 두세요.
        - 도구가 실패하면 이유를 그대로 쉽게 설명하세요. 성공했다고 지어내지 마세요.
        - 다른 사용자의 정보나 이 플랫폼과 관계없는 요청은 정중히 거절하세요.
        - 모르는 것은 모른다고 말하세요.
        """;

    /**
     * Gemini 연결(Google GenAI SDK) 클라이언트.
     *
     * <p>[왜 직접 만드나] SDK의 기본 설정은 429(사용량 한도 초과)에도 여러 번 다시 시도하며 점점 길게 기다려서,
     * 무료 한도가 다 되면 사용자가 30~40초 뒤에야 실패를 알게 된다. 재시도를 1번(= 다시 시도하지 않음)으로 줄여
     * 한도가 다 되면 바로 실패시키고, 채팅 위젯이 곧바로 버튼 메뉴로 넘어가게 한다.
     * 이 빈이 있으면 Spring AI의 자동 설정 클라이언트는 만들어지지 않는다.
     */
    @Bean
    public Client geminiClient(Environment environment) {
        // @Value("${...}")로 받으면 이 빈에서는 자리표시자가 풀리지 않은 채 들어와 "API key not valid"(400)가 났다.
        // Environment에서 직접 읽으면 application.yaml의 ${GEMINI_API_KEY}까지 제대로 풀린다.
        String apiKey = environment.getProperty("spring.ai.google.genai.api-key");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY가 설정되지 않았습니다. .env를 확인해 주세요.");
        }
        return Client.builder()
            .apiKey(apiKey)
            .httpOptions(HttpOptions.builder()
                .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                .build())
            .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem(SYSTEM_PROMPT).build();
    }
}
