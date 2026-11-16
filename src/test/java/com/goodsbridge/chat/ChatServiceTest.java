package com.goodsbridge.chat;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge.shop.ShopRepository;
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
    @Mock
    private ActionConfirmationGuard confirmationGuard;
    @Mock
    private ShopRepository shopRepository;

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
        service = new ChatService(chatClient, chatLogService, exchangeAgentTools, confirmationGuard, shopRepository);
    }

    private void lenient(ChatClient client, ChatClient.ChatClientRequestSpec spec, ChatClient.CallResponseSpec call) {
        org.mockito.Mockito.lenient().when(client.prompt()).thenReturn(spec);
        org.mockito.Mockito.lenient().when(spec.call()).thenReturn(call);
    }

    @Test
    void chat_shopOwnersGetAnUpdateNoticeWithoutCallingAiOrSaving() {
        when(shopRepository.existsByOwnerUserIdAndDeletedFalse(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.chat(USER_ID, "안녕"))
            .isInstanceOf(ForbiddenException.class)
            .hasMessage(ChatService.SHOP_NOT_READY_MESSAGE)
            .hasMessageContaining("업데이트");

        // 가게 계정은 AI를 부르지 않으므로 사용량을 쓰지 않고 대화 기록도 남기지 않는다
        verifyNoInteractions(chatClient, chatLogService);
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
    void chat_savesQuestionAndReplyTogetherOnlyAfterTheAiAnswers() {
        when(callSpec.content()).thenReturn("안녕하세요, 무엇을 도와드릴까요?");

        ChatResponse response = service.chat(USER_ID, "  안녕  ");

        assertThat(response.getReply()).isEqualTo("안녕하세요, 무엇을 도와드릴까요?");
        // AI 호출 → (답을 받은 뒤) 질문과 답변을 한꺼번에 저장. 질문은 앞뒤 공백을 뺀 채 저장된다.
        InOrder order = inOrder(callSpec, chatLogService);
        order.verify(callSpec).content();
        order.verify(chatLogService).saveConversation(eq(USER_ID), eq("안녕"), any(), eq("안녕하세요, 무엇을 도와드릴까요?"));
    }

    @Test
    void chat_savesTheToolRecordsTheAiMadeDuringTheTurn() {
        // 도구가 이번 턴의 목록(toolNotes)에 적으면, 답을 받았을 때 질문·답변과 함께 저장된다
        java.util.concurrent.atomic.AtomicReference<Map<String, Object>> context = new java.util.concurrent.atomic.AtomicReference<>();
        when(requestSpec.toolContext(any())).thenAnswer(invocation -> {
            context.set(invocation.getArgument(0));
            return requestSpec;
        });
        when(callSpec.content()).thenAnswer(invocation -> {
            ((List<String>) context.get().get("toolNotes")).add("교환 신청 조회: 2건");
            return "2건이 있어요.";
        });

        service.chat(USER_ID, "내 교환 보여줘");

        verify(chatLogService).saveConversation(USER_ID, "내 교환 보여줘", List.of("교환 신청 조회: 2건"), "2건이 있어요.");
    }

    @Test
    void chat_passesLoggedInUserIdToToolsThroughToolContext() {
        when(callSpec.content()).thenReturn("ok");

        service.chat(USER_ID, "내 교환 보여줘");

        // 도구가 쓸 사용자 id는 AI가 정하는 값이 아니라 서버(로그인 정보)가 넘긴다
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(requestSpec).toolContext(captor.capture());
        assertThat(captor.getValue()).containsEntry("userId", USER_ID);
        // 요청마다 다른 turnId가 들어간다 (취소 확인과 실행이 같은 턴인지 가리는 데 쓴다)
        assertThat(captor.getValue().get("turnId")).isInstanceOf(String.class);
        // 도구가 실행 기록을 적을 목록도 들어간다
        assertThat(captor.getValue().get("toolNotes")).isInstanceOf(List.class);
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
    void chat_turnsAiFailureIntoServiceUnavailableAndSavesNothing() {
        when(callSpec.content()).thenThrow(new IllegalStateException("API key not valid"));

        assertThatThrownBy(() -> service.chat(USER_ID, "안녕"))
            .isInstanceOf(ServiceUnavailableException.class)
            // 원인(키 오류 같은 내부 정보)은 사용자에게 노출하지 않는다
            .hasMessageNotContaining("API key");

        // 답을 못 받았으니 질문도 저장하지 않는다
        verify(chatLogService, never()).saveConversation(any(), any(), any(), any());
    }

    @Test
    void chat_completesTheTurnOnlyAfterTheReplyIsSaved() {
        when(callSpec.content()).thenReturn("취소하면 보증금이 환불돼요. 정말 취소할까요?");

        service.chat(USER_ID, "5번 취소해줘");

        // 설명(답변)이 저장·전달된 뒤에야 이 턴의 취소 확인이 인정된다
        InOrder order = inOrder(chatLogService, confirmationGuard);
        order.verify(chatLogService).saveConversation(eq(USER_ID), any(), any(), any());
        order.verify(confirmationGuard).completeTurn(eq(USER_ID), any());
        verify(confirmationGuard, never()).abortTurn(anyLong(), any());
    }

    @Test
    void chat_discardsTheTurnsCancelConfirmationWhenAiFails() {
        when(callSpec.content()).thenThrow(new IllegalStateException("quota"));

        assertThatThrownBy(() -> service.chat(USER_ID, "5번 취소해줘"))
            .isInstanceOf(ServiceUnavailableException.class);

        verify(confirmationGuard).abortTurn(eq(USER_ID), any());
        verify(confirmationGuard, never()).completeTurn(anyLong(), any());
    }

    @Test
    void chat_tellsTheUserWhenTheAiQuotaIsExhausted() {
        // Spring AI가 SDK의 429 오류를 한 겹 감싸서 던지는 모양을 흉내 낸다
        RuntimeException wrapped = new RuntimeException("Failed to generate content",
            new com.google.genai.errors.ClientException(429, "RESOURCE_EXHAUSTED", "You exceeded your current quota"));
        when(callSpec.content()).thenThrow(wrapped);

        assertThatThrownBy(() -> service.chat(USER_ID, "안녕"))
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessage(ChatService.QUOTA_MESSAGE)
            .hasMessageContaining("한도")
            .hasMessageNotContaining("RESOURCE_EXHAUSTED");   // SDK의 원문은 노출하지 않는다
    }

    @Test
    void chat_usesTheGenericMessageForOtherAiFailures() {
        when(callSpec.content()).thenThrow(new RuntimeException("Failed to generate content",
            new com.google.genai.errors.ClientException(400, "INVALID_ARGUMENT", "API key not valid")));

        assertThatThrownBy(() -> service.chat(USER_ID, "안녕"))
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessage(ChatService.UNAVAILABLE_MESSAGE)
            .hasMessageNotContaining("한도");
    }

    @Test
    void chat_usesFallbackMessageWhenAiReturnsNothing() {
        when(callSpec.content()).thenReturn("  ");

        ChatResponse response = service.chat(USER_ID, "안녕");

        assertThat(response.getReply()).contains("답변을 만들지 못했어요");
        // 사용자는 취소 내용을 설명받지 못했으니 확인을 인정하지 않고, 답을 못 받았으니 질문도 저장하지 않는다
        verify(confirmationGuard).abortTurn(eq(USER_ID), any());
        verify(chatLogService, never()).saveConversation(any(), any(), any(), any());
    }

    @Test
    void clearHistory_deletesMyLogsAndDiscardsPendingConfirmations() {
        service.clearHistory(USER_ID);

        verify(chatLogService).deleteAll(USER_ID);
        // 초기화 전에 설명받은 취소가 "네"로 실행되는 일이 없게 확인 대기도 버린다
        verify(confirmationGuard).clearUser(USER_ID);
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
