package com.goodsbridge.chat;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 에이전트 API (이슈 #13). 로그인이 필요하다 (SecurityConfig의 anyRequest().authenticated()).
 * AI가 쓰는 도구(@Tool)는 REST로 노출하지 않고, 이 /api/chat 하나만 외부에 열려 있다.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // 메시지 전송 → AI 답변
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return chatService.chat(SecurityUtil.getCurrentUserId(), request.getContent());
    }

    // 오늘 남은 AI 질문 횟수
    @GetMapping("/usage")
    public ChatUsageResponse usage() {
        return chatService.usage(SecurityUtil.getCurrentUserId());
    }

    // 대화 기록 (본인 것 중 최근 50건)
    @GetMapping("/history")
    public List<ChatLogResponse> history() {
        return chatService.history(SecurityUtil.getCurrentUserId());
    }

    // 대화 초기화: 내 대화 기록을 모두 지운다 (환불·취소 같은 교환 내역은 그대로 남는다)
    @DeleteMapping("/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory() {
        chatService.clearHistory(SecurityUtil.getCurrentUserId());
    }
}
