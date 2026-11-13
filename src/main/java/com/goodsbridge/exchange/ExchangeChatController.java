package com.goodsbridge.exchange;

import com.goodsbridge._core.util.SecurityUtil;
import com.goodsbridge.exchange.dto.ExchangeChatSendRequest;
import com.goodsbridge.exchange.dto.ExchangeMeetingRequest;
import com.goodsbridge.exchange.dto.ExchangeMessageResponse;
import com.goodsbridge.exchange.dto.ExchangeRequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

// 교환 확정 후 채팅(WebSocket/STOMP)과 만날 시간 제안/확정(REST).
// 채팅/시간 변경은 같은 토픽(/topic/exchange-requests/{id})으로 상대에게 즉시 전달된다.
@RestController
@RequiredArgsConstructor
public class ExchangeChatController {

    private static final String TOPIC = "/topic/exchange-requests/";

    // 토픽으로 내려가는 이벤트: CHAT(새 메시지) 또는 MEETING(만날 시간 변경)
    private record Event(String type, ExchangeMessageResponse message, ExchangeRequestResponse request) {}

    private final ExchangeChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    // 채팅 이력
    @GetMapping("/api/exchange-requests/{id}/messages")
    public ResponseEntity<List<ExchangeMessageResponse>> getMessages(@PathVariable Long id) {
        return ResponseEntity.ok(chatService.getMessages(id, SecurityUtil.getCurrentUserId()));
    }

    // 채팅 전송: 클라이언트 SEND /app/exchange-requests/{id}/chat
    @MessageMapping("/exchange-requests/{id}/chat")
    public void chat(@DestinationVariable Long id, @Payload ExchangeChatSendRequest request, Principal principal) {
        Long userId = Long.valueOf(principal.getName());
        ExchangeMessageResponse message = chatService.sendMessage(id, userId, request.getContent());
        messagingTemplate.convertAndSend(TOPIC + id, new Event("CHAT", message, null));
    }

    // 만날 시간 제안
    @PostMapping("/api/exchange-requests/{id}/meeting")
    public ResponseEntity<ExchangeRequestResponse> proposeMeeting(@PathVariable Long id,
                                                                  @RequestBody ExchangeMeetingRequest request) {
        request.validate();
        ExchangeRequestResponse response = chatService.proposeMeeting(id, SecurityUtil.getCurrentUserId(), request.getMeetingAt());
        messagingTemplate.convertAndSend(TOPIC + id, new Event("MEETING", null, response));
        return ResponseEntity.ok(response);
    }

    // 상대가 제안한 시간 확정
    @PostMapping("/api/exchange-requests/{id}/meeting/confirm")
    public ResponseEntity<ExchangeRequestResponse> confirmMeeting(@PathVariable Long id) {
        ExchangeRequestResponse response = chatService.confirmMeeting(id, SecurityUtil.getCurrentUserId());
        messagingTemplate.convertAndSend(TOPIC + id, new Event("MEETING", null, response));
        return ResponseEntity.ok(response);
    }
}
