package com.goodsbridge.chat;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatLogService chatLogService;
    @Mock
    private ExchangeAgentTools exchangeAgentTools;

    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;
    private ChatService service;

    @BeforeEach
    void setUp() {
        // ChatClient는 prompt().user(..).call().content() 처럼 이어 붙이는 구조라서,
        // 요청 단계(requestSpec)는 "호출하면 자기 자신을 돌려주는" 목으로 만들고 마지막 답변만 정한다.
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        callSpec = mock(ChatClient.CallResponseSpec.class);
        lenient(chatClient, requestSpec, callSpec);
        service = new ChatService(chatClient, chatLogService, exchangeAgentTools);
    }

    private void lenient(ChatClient client, ChatClient.ChatClientRequestSpec spec, ChatClient.CallResponseSpec call) {
        org.mockito.Mockito.lenient().when(client.prompt()).thenReturn(spec);
        org.mockito.Mockito.lenient().when(spec.call()).thenReturn(call);
    }

    @Test
    void chat_rejectsBlankContentWithoutCallingAiOrSaving() {
        assertThatThrownBy(() -> service.chat(USER_ID, "   "))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.chat(USER_ID, null))
            .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(chatClient, chatLogService);
    }

    @Test
    void chat_rejectsTooLongContent() {
        String tooLong = "가".repeat(1001);

        assertThatThrownBy(() -> service.chat(USER_ID, tooLong))
            .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(chatClient);
    }

    @Test
    void chat_savesUserMessageBeforeAiCallAndReplyAfter() {
        when(callSpec.content()).thenReturn("안녕하세요, 무엇을 도와드릴까요?");

        ChatResponse response = service.chat(USER_ID, "  안녕  ");

        assertThat(response.getReply()).isEqualTo("안녕하세요, 무엇을 도와드릴까요?");
        // 사용자 메시지(앞뒤 공백 제거) 저장 → AI 호출 → 답변 저장 순서
        InOrder order = inOrder(chatLogService, callSpec);
        order.verify(chatLogService).save(USER_ID, ChatRole.USER, "안녕");
        order.verify(callSpec).content();
        order.verify(chatLogService).save(USER_ID, ChatRole.ASSISTANT, "안녕하세요, 무엇을 도와드릴까요?");
    }

    @Test
    void chat_passesLoggedInUserIdToToolsThroughToolContext() {
        when(callSpec.content()).thenReturn("ok");

        service.chat(USER_ID, "내 교환 보여줘");

        // 도구가 쓸 사용자 id는 AI가 정하는 값이 아니라 서버(로그인 정보)가 넘긴다
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(requestSpec).toolContext(captor.capture());
        assertThat(captor.getValue()).containsEntry("userId", USER_ID);
        verify(requestSpec).user("내 교환 보여줘");
    }

    @Test
    void chat_sendsRecentConversationAsHistoryInOrder() {
        ChatLog asked = log(ChatRole.USER, "교환 현황 알려줘");
        ChatLog answered = log(ChatRole.ASSISTANT, "3건이 있어요.");
        when(chatLogService.findRecentConversation(USER_ID)).thenReturn(List.of(asked, answered));
        when(callSpec.content()).thenReturn("네");

        service.chat(USER_ID, "그중 대기 중인 건?");

        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(requestSpec).messages(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(0)).isInstanceOf(UserMessage.class);
        assertThat(captor.getValue().get(0).getText()).isEqualTo("교환 현황 알려줘");
        assertThat(captor.getValue().get(1)).isInstanceOf(AssistantMessage.class);
    }

    @Test
    void chat_turnsAiFailureIntoServiceUnavailableAndDoesNotSaveReply() {
        when(callSpec.content()).thenThrow(new IllegalStateException("API key not valid"));

        assertThatThrownBy(() -> service.chat(USER_ID, "안녕"))
            .isInstanceOf(ServiceUnavailableException.class)
            // 원인(키 오류 같은 내부 정보)은 사용자에게 노출하지 않는다
            .hasMessageNotContaining("API key");

        verify(chatLogService, never()).save(anyLong(), eq(ChatRole.ASSISTANT), any());
    }

    @Test
    void chat_usesFallbackMessageWhenAiReturnsNothing() {
        when(callSpec.content()).thenReturn("  ");

        ChatResponse response = service.chat(USER_ID, "안녕");

        assertThat(response.getReply()).contains("답변을 만들지 못했어요");
    }

    @Test
    void history_returnsOnlyTheLoggedInUsersLogs() {
        service.history(USER_ID);

        verify(chatLogService).findHistory(USER_ID);
    }

    private ChatLog log(ChatRole role, String content) {
        return ChatLog.builder().role(role).content(content).build();
    }
}
