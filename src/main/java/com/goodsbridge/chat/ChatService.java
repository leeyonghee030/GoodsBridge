package com.goodsbridge.chat;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ServiceUnavailableException;
import com.google.genai.errors.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 에이전트 대화의 핵심 흐름.
 *
 * <pre>
 * 사용자 메시지
 *   → (1) 내용 검사
 *   → (2) 앞선 대화(최근 10건)를 불러오기
 *   → (3) 사용자 메시지를 ChatLog에 저장
 *   → (4) Gemini 호출  ← 필요하면 AI가 @Tool(교환 조회·취소)을 스스로 호출한다
 *   → (5) AI 답변을 ChatLog에 저장하고 돌려주기
 * </pre>
 *
 * <p>트랜잭션을 걸지 않은 이유: AI 호출은 몇 초 걸릴 수 있어서 DB 연결을 붙잡고 기다리지 않게,
 * 저장은 {@link ChatLogService}가 건마다 짧게 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 도구(@Tool)에 로그인 사용자 id를 넘길 때 쓰는 키. ExchangeAgentTools가 같은 키로 꺼낸다. */
    static final String USER_ID_KEY = "userId";
    /** 대화 한 번(요청 하나)의 식별자를 도구에 넘길 때 쓰는 키. 취소 확인과 실행이 같은 턴인지 가리는 데 쓴다. */
    static final String TURN_ID_KEY = "turnId";

    private static final int MAX_CONTENT_LENGTH = 1000;

    // AI를 쓸 수 없을 때 사용자에게 보여 주는 안내. 채팅 위젯이 이 문장과 함께 같은 기능의 버튼을 보여 준다.
    static final String QUOTA_MESSAGE = "AI 사용 한도가 모두 소진되어 지금은 AI 답변을 쓸 수 없어요. 대신 아래 버튼으로 같은 기능을 쓸 수 있어요.";
    static final String UNAVAILABLE_MESSAGE = "AI 응답을 가져오지 못했어요. 아래 버튼으로 같은 기능을 쓰거나 잠시 후 다시 시도해 주세요.";

    private final ChatClient chatClient;
    private final ChatLogService chatLogService;
    private final ExchangeAgentTools exchangeAgentTools;
    private final ActionConfirmationGuard confirmationGuard;

    public ChatResponse chat(Long userId, String content) {
        // (1) 내용 검사: 비어 있으면 안 되고 너무 길면 거절한다 (AI 호출 비용 보호)
        String message = content == null ? "" : content.trim();
        if (message.isEmpty()) {
            throw new BadRequestException("내용을 입력해 주세요.");
        }
        if (message.length() > MAX_CONTENT_LENGTH) {
            throw new BadRequestException("메시지는 " + MAX_CONTENT_LENGTH + "자 이하로 입력해 주세요.");
        }

        // (2) 앞선 대화를 먼저 읽어 둔다 (방금 보낸 메시지가 중복으로 들어가지 않게 저장보다 먼저)
        List<Message> history = chatLogService.findRecentConversation(userId).stream()
            .map(this::toMessage)
            .toList();

        // (3) 사용자 메시지 저장. AI가 도구를 실행하면 TOOL 기록이 이 뒤에 쌓여서 순서가 맞는다.
        chatLogService.save(userId, ChatRole.USER, message);

        // (4) Gemini 호출
        String turnId = UUID.randomUUID().toString();   // 이번 요청(대화 한 턴)의 식별자 (이중 확인용)
        String reply;
        try {
            reply = chatClient.prompt()
                .messages(history)                                  // 앞선 대화
                .user(message)                                      // 이번 질문
                .tools(exchangeAgentTools)                          // AI가 쓸 수 있는 도구
                .toolContext(Map.of(USER_ID_KEY, userId,            // 도구에 로그인 사용자 id 전달 (AI가 바꿀 수 없다)
                    TURN_ID_KEY, turnId))                           // 취소 확인과 실행이 같은 턴인지 가리는 데 쓴다
                .call()
                .content();
        } catch (RuntimeException e) {
            // API 키 오류, 한도 초과, 네트워크 문제 등. 원인은 서버 로그에 남기고 사용자에게는 일반 안내만 한다.
            confirmationGuard.abortTurn(userId, turnId);   // 설명이 전달되지 않았으니 이 턴의 취소 확인은 버린다
            log.warn("AI 호출 실패 - userId={}", userId, e);   // 예외 전체(원인 포함)를 남겨야 키·한도·모델 문제를 구분할 수 있다
            // 한도 소진(429)이면 그렇다고 알려 준다. 키 오류 같은 그 밖의 내부 정보는 노출하지 않는다.
            throw new ServiceUnavailableException(isQuotaExceeded(e) ? QUOTA_MESSAGE : UNAVAILABLE_MESSAGE);
        }
        if (reply == null || reply.isBlank()) {
            confirmationGuard.abortTurn(userId, turnId);   // 사용자는 취소 내용을 설명받지 못했다
            reply = "죄송해요, 답변을 만들지 못했어요. 다시 한 번 말씀해 주시겠어요?";
        }

        // (5) AI 답변 저장. 답변이 사용자에게 전달되는 시점이므로 이 턴의 취소 확인을 인정한다.
        chatLogService.save(userId, ChatRole.ASSISTANT, reply);
        confirmationGuard.completeTurn(userId, turnId);
        return new ChatResponse(reply);
    }

    // 예외의 원인 사슬 어딘가에 Gemini의 429(사용량 한도 초과)가 있는지 본다.
    private boolean isQuotaExceeded(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 10; depth++, current = current.getCause()) {
            if (current instanceof ApiException api && api.code() == 429) {
                return true;
            }
        }
        return false;
    }

    // 대화 기록 조회: 로그인한 본인 것만 (userId로 걸러서 가져오므로 남의 대화는 나오지 않는다)
    public List<ChatLogResponse> history(Long userId) {
        return chatLogService.findHistory(userId);
    }

    // ChatLog → Spring AI 메시지. USER는 사용자 말풍선, ASSISTANT는 AI 말풍선으로 바꾼다.
    private Message toMessage(ChatLog log) {
        return log.getRole() == ChatRole.USER
            ? new UserMessage(log.getContent())
            : new AssistantMessage(log.getContent());
    }
}
