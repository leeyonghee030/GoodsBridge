package com.goodsbridge.chat;

import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge.exchange.ExchangeRequestStatus;
import com.goodsbridge.exchange.ExchangeService;
import com.goodsbridge.exchange.dto.ExchangeRequestResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeAgentToolsTest {

    private static final Long USER_ID = 7L;
    private static final ToolContext CONTEXT = new ToolContext(Map.of("userId", USER_ID));

    @Mock
    private ExchangeService exchangeService;
    @Mock
    private ChatLogService chatLogService;
    @InjectMocks
    private ExchangeAgentTools tools;

    private ExchangeRequestResponse request(Long id, Long proposerId, LocalDateTime createdAt) {
        return ExchangeRequestResponse.builder()
            .id(id).proposerUserId(proposerId).status(ExchangeRequestStatus.PENDING)
            .itemTitle("포토카드 A").itemBTitle("포토카드 B").shopName("성수점")
            .createdAt(createdAt).build();
    }

    // ===== 조회 도구 =====

    @Test
    void getMyExchangeRequests_returnsLatestFirstAndMarksMyRole() {
        LocalDateTime now = LocalDateTime.now();
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(
            request(1L, USER_ID, now.minusDays(2)),     // 내가 신청한 것
            request(2L, 99L, now)));                    // 내 게시글에 들어온 신청

        List<ExchangeAgentTools.ExchangeInfo> result = tools.getMyExchangeRequests(CONTEXT);

        assertThat(result).extracting(ExchangeAgentTools.ExchangeInfo::requestId).containsExactly(2L, 1L);
        assertThat(result.get(0).myRole()).isEqualTo("게시글 주인");
        assertThat(result.get(1).myRole()).isEqualTo("신청자");
        verify(chatLogService).save(USER_ID, ChatRole.TOOL, "교환 신청 조회: 2건");
    }

    @Test
    void getMyExchangeRequests_limitsToTenRequests() {
        List<ExchangeRequestResponse> many = new ArrayList<>();
        for (long i = 1; i <= 15; i++) {
            many.add(request(i, USER_ID, LocalDateTime.now().minusMinutes(i)));
        }
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(many);

        assertThat(tools.getMyExchangeRequests(CONTEXT)).hasSize(10);
    }

    // ===== 취소 도구 =====

    @Test
    void cancelExchangeRequest_cancelsAsTheLoggedInUserAndLogsIt() {
        String message = tools.cancelExchangeRequest(5L, CONTEXT);

        // 취소하는 사람은 AI가 넘긴 값이 아니라 도구 컨텍스트(로그인 사용자)다
        verify(exchangeService).cancelExchange(5L, USER_ID);
        assertThat(message).contains("5번").contains("취소했습니다");
        verify(chatLogService).save(USER_ID, ChatRole.TOOL, "교환 신청 5번 취소");
    }

    @Test
    void cancelExchangeRequest_explainsExpectedFailuresInsteadOfThrowing() {
        doThrow(new ForbiddenException("교환 신청자만 교환 취소 할 수있습니다"))
            .when(exchangeService).cancelExchange(5L, USER_ID);

        String message = tools.cancelExchangeRequest(5L, CONTEXT);

        assertThat(message).startsWith("취소하지 못했습니다").contains("교환 신청자만");
        verify(chatLogService).save(eq(USER_ID), eq(ChatRole.TOOL), contains("취소 실패"));
    }

    @Test
    void tools_failWithoutLoggedInUserInContext() {
        ToolContext empty = new ToolContext(Map.of());

        assertThatThrownBy(() -> tools.cancelExchangeRequest(5L, empty))
            .isInstanceOf(IllegalStateException.class);
        verify(exchangeService, never()).cancelExchange(anyLong(), any());
    }

    // ===== Spring AI가 @Tool을 실제로 인식하는지 (Gemini 없이 확인) =====

    @Test
    void springAi_discoversBothToolsAndHidesToolContextFromTheModel() {
        org.springframework.ai.tool.ToolCallback[] callbacks = org.springframework.ai.support.ToolCallbacks.from(tools);

        assertThat(callbacks).extracting(c -> c.getToolDefinition().name())
            .containsExactlyInAnyOrder("getMyExchangeRequests", "cancelExchangeRequest");
        org.springframework.ai.tool.ToolCallback cancel = java.util.Arrays.stream(callbacks)
            .filter(c -> c.getToolDefinition().name().equals("cancelExchangeRequest")).findFirst().orElseThrow();
        // AI에게 보이는 입력은 신청 번호뿐이다. 로그인 사용자 정보(ToolContext)는 AI가 볼 수도 바꿀 수도 없다.
        assertThat(cancel.getToolDefinition().inputSchema()).contains("exchangeRequestId").doesNotContain("userId");
    }

    @Test
    void springAi_callingTheCancelToolRunsTheServiceAsTheLoggedInUser() {
        org.springframework.ai.tool.ToolCallback cancel = java.util.Arrays.stream(org.springframework.ai.support.ToolCallbacks.from(tools))
            .filter(c -> c.getToolDefinition().name().equals("cancelExchangeRequest")).findFirst().orElseThrow();

        // AI가 보내는 JSON 입력을 흉내 낸다. 그 안에 userId를 넣어도 쓰이지 않는다.
        String result = cancel.call("{\"exchangeRequestId\": 5, \"userId\": 999}", CONTEXT);

        verify(exchangeService).cancelExchange(5L, USER_ID);
        assertThat(result).contains("취소했습니다");
    }
}
