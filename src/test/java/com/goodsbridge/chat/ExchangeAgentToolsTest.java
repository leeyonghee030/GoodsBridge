package com.goodsbridge.chat;

import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge.exchange.ExchangeRequestStatus;
import com.goodsbridge.exchange.ExchangeService;
import com.goodsbridge.exchange.dto.ExchangeRequestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock
    private ExchangeService exchangeService;
    private ActionConfirmationGuard guard;
    private List<String> notes;   // ChatService가 만들어 도구에 넘기는 "이번 턴의 도구 실행 기록" 목록
    private ExchangeAgentTools tools;

    @BeforeEach
    void setUp() {
        // 이중 확인 장치는 진짜를 쓴다 (확인 → 설명 전달 → 다음 턴 동의 → 실행 규칙을 같이 검증하려고)
        guard = new ActionConfirmationGuard();
        notes = new ArrayList<>();
        tools = new ExchangeAgentTools(exchangeService, guard);
    }

    // ChatService가 답변을 사용자에게 무사히 전달하고 한 턴을 끝내는 것을 흉내 낸다
    private void endTurn(String turnId) {
        guard.completeTurn(USER_ID, turnId);
    }

    // 대화 한 번(턴)마다 turnId가 다르다. 같은 turnId면 같은 요청 안이다.
    private ToolContext turn(String turnId) {
        return new ToolContext(Map.of("userId", USER_ID, "turnId", turnId, "toolNotes", notes));
    }

    private ExchangeRequestResponse request(Long id, Long proposerId, LocalDateTime createdAt, ExchangeRequestStatus status, Integer deposit) {
        return ExchangeRequestResponse.builder()
            .id(id).proposerUserId(proposerId).status(status).depositAmountB(deposit)
            .itemTitle("포토카드 A").itemBTitle("포토카드 B").shopName("성수점")
            .createdAt(createdAt).build();
    }

    private ExchangeRequestResponse myPending(Long id) {
        return request(id, USER_ID, LocalDateTime.now(), ExchangeRequestStatus.PENDING, 5000);
    }

    // ===== 조회 도구: 바로 답한다 (기본은 대기 중, 분류별 건수 요약, 전체 내역 링크) =====

    private ExchangeRequestResponse requestWithStatus(Long id, ExchangeRequestStatus status, LocalDateTime createdAt) {
        return request(id, USER_ID, createdAt, status, 5000);
    }

    @Test
    void getMyExchangeRequests_showsOnlyPendingByDefaultNewestFirstAndMarksMyRole() {
        LocalDateTime now = LocalDateTime.now();
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(
            request(1L, USER_ID, now.minusDays(2), ExchangeRequestStatus.PENDING, 5000),   // 내가 신청한 것
            request(2L, 99L, now, ExchangeRequestStatus.PENDING, 5000),                    // 내 게시글에 들어온 신청
            requestWithStatus(3L, ExchangeRequestStatus.CONFIRMED, now),
            requestWithStatus(4L, ExchangeRequestStatus.CANCELLED, now)));

        ExchangeAgentTools.ExchangeList result = tools.getMyExchangeRequests(null, turn("t1"));

        assertThat(result.category()).isEqualTo("PENDING");
        // 처리할 일이 있는 '대기 중'만 보여 준다 (확정·취소는 빠진다)
        assertThat(result.requests()).extracting(ExchangeAgentTools.ExchangeInfo::requestId).containsExactly(2L, 1L);
        assertThat(result.requests().get(0).myRole()).isEqualTo("게시글 주인");
        assertThat(result.requests().get(1).myRole()).isEqualTo("신청자");
        assertThat(result.requests().get(0).status()).isEqualTo("대기 중");   // 사용자가 읽는 이름
        assertThat(notes).containsExactly("교환 신청 조회: 대기 중 2건");
    }

    @Test
    void getMyExchangeRequests_summarizesCountsPerCategoryAndLinksToTheFullHistory() {
        LocalDateTime now = LocalDateTime.now();
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(
            requestWithStatus(1L, ExchangeRequestStatus.PENDING, now),
            requestWithStatus(2L, ExchangeRequestStatus.CONFIRMED, now),
            requestWithStatus(3L, ExchangeRequestStatus.CONFIRMED, now),
            requestWithStatus(4L, ExchangeRequestStatus.COMPLETED, now),
            requestWithStatus(5L, ExchangeRequestStatus.CANCELLED, now),
            requestWithStatus(6L, ExchangeRequestStatus.REJECTED, now),
            requestWithStatus(7L, ExchangeRequestStatus.PROPOSAL_EXPIRED, now),
            requestWithStatus(8L, ExchangeRequestStatus.EXPIRED_BOTH, now)));

        ExchangeAgentTools.ExchangeList result = tools.getMyExchangeRequests(null, turn("t1"));

        // 분류별 건수: 8가지 상태를 사용자가 알아보기 쉬운 4가지로 묶는다
        assertThat(result.countsByCategory()).containsExactly(
            java.util.Map.entry("대기 중", 1L), java.util.Map.entry("확정", 2L),
            java.util.Map.entry("완료", 1L), java.util.Map.entry("취소·거절·만료", 4L));
        assertThat(result.totalCount()).isEqualTo(8);
        // 보여 주지 않은 상태는 전체 내역 화면으로 안내한다
        assertThat(result.allHistoryLink()).isEqualTo("/my/exchange");
    }

    @Test
    void getMyExchangeRequests_showsAnotherCategoryWhenAsked() {
        LocalDateTime now = LocalDateTime.now();
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(
            requestWithStatus(1L, ExchangeRequestStatus.PENDING, now),
            requestWithStatus(2L, ExchangeRequestStatus.CONFIRMED, now),
            requestWithStatus(3L, ExchangeRequestStatus.EXPIRED_PARTIAL, now),
            requestWithStatus(4L, ExchangeRequestStatus.CANCELLED, now.minusDays(1))));

        assertThat(tools.getMyExchangeRequests("CONFIRMED", turn("t1")).requests())
            .extracting(ExchangeAgentTools.ExchangeInfo::requestId).containsExactly(2L);
        // 취소·거절·만료는 한 분류로 묶여서 나온다
        assertThat(tools.getMyExchangeRequests("closed", turn("t2")).requests())
            .extracting(ExchangeAgentTools.ExchangeInfo::requestId).containsExactly(3L, 4L);
    }

    @Test
    void getMyExchangeRequests_fallsBackToPendingForUnknownCategory() {
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(
            requestWithStatus(1L, ExchangeRequestStatus.PENDING, LocalDateTime.now())));

        assertThat(tools.getMyExchangeRequests("뭔가 이상한 값", turn("t1")).category()).isEqualTo("PENDING");
    }

    @Test
    void getMyExchangeRequests_limitsToTenAndSaysThereIsMore() {
        List<ExchangeRequestResponse> many = new ArrayList<>();
        for (long i = 1; i <= 15; i++) {
            many.add(request(i, USER_ID, LocalDateTime.now().minusMinutes(i), ExchangeRequestStatus.PENDING, 5000));
        }
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(many);

        ExchangeAgentTools.ExchangeList result = tools.getMyExchangeRequests(null, turn("t1"));

        assertThat(result.requests()).hasSize(10);
        assertThat(result.categoryCount()).isEqualTo(15);
        assertThat(result.hasMore()).isTrue();
    }

    @Test
    void getMyExchangeRequests_hasNoMoreWhenEverythingFits() {
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(
            requestWithStatus(1L, ExchangeRequestStatus.PENDING, LocalDateTime.now())));

        assertThat(tools.getMyExchangeRequests(null, turn("t1")).hasMore()).isFalse();
    }

    // ===== 1단계: 확인 도구 =====

    @Test
    void prepareCancel_explainsWhatHappensAndDoesNotCancel() {
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(myPending(5L)));

        ExchangeAgentTools.CancelPreview preview = tools.prepareCancelExchangeRequest(5L, turn("t1"));

        assertThat(preview.cancellable()).isTrue();
        assertThat(preview.refundAmount()).isEqualTo(5000);
        // 취소하면 어떻게 되는지(환불 금액, 되돌릴 수 없음)를 AI가 사용자에게 설명할 수 있게 담는다
        assertThat(preview.whatHappens()).contains("5000원").contains("되돌릴 수 없");
        verify(exchangeService, never()).cancelExchange(anyLong(), any());
    }

    @Test
    void prepareCancel_refusesRequestThatIsNotMine() {
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of());

        ExchangeAgentTools.CancelPreview preview = tools.prepareCancelExchangeRequest(999L, turn("t1"));

        assertThat(preview.cancellable()).isFalse();
        assertThat(preview.problem()).contains("없는 번호");
    }

    @Test
    void prepareCancel_refusesWhenIAmTheOwnerNotTheProposer() {
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(
            request(5L, 99L, LocalDateTime.now(), ExchangeRequestStatus.PENDING, 5000)));

        ExchangeAgentTools.CancelPreview preview = tools.prepareCancelExchangeRequest(5L, turn("t1"));

        assertThat(preview.cancellable()).isFalse();
        assertThat(preview.problem()).contains("신청한 본인");
    }

    @Test
    void prepareCancel_refusesRequestThatIsNotPending() {
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(
            request(5L, USER_ID, LocalDateTime.now(), ExchangeRequestStatus.CONFIRMED, 5000)));

        ExchangeAgentTools.CancelPreview preview = tools.prepareCancelExchangeRequest(5L, turn("t1"));

        assertThat(preview.cancellable()).isFalse();
        assertThat(preview.problem()).contains("대기 중인 신청만");
    }

    // ===== 2단계: 실행 도구 (이중 확인을 서버가 강제) =====

    @Test
    void cancel_runsOnlyAfterPrepareInAnEarlierTurn() {
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(myPending(5L)));
        tools.prepareCancelExchangeRequest(5L, turn("turn-1"));          // 1) 확인하고 사용자에게 설명
        endTurn("turn-1");                                                // 설명이 사용자에게 전달됨
        // 2) 사용자가 "네"라고 답한 다음 턴에서 실행

        String message = tools.cancelExchangeRequest(5L, turn("turn-2"));

        // 취소하는 사람은 AI가 넘긴 값이 아니라 도구 컨텍스트(로그인 사용자)다
        verify(exchangeService).cancelExchange(5L, USER_ID);
        assertThat(message).contains("5번").contains("취소했습니다");
        assertThat(notes).contains("교환 신청 5번 취소");
    }

    @Test
    void cancel_isRefusedWithoutAnyPrepare() {
        String message = tools.cancelExchangeRequest(5L, turn("turn-1"));

        verify(exchangeService, never()).cancelExchange(anyLong(), any());
        assertThat(message).contains("취소하지 않았습니다").contains("동의");
    }

    @Test
    void cancel_isRefusedWhenPrepareAndCancelHappenInTheSameTurn() {
        // AI가 한 번의 답변 안에서 확인과 실행을 연달아 하는 경우: 사용자가 동의할 기회가 없었으므로 막는다
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(myPending(5L)));
        tools.prepareCancelExchangeRequest(5L, turn("turn-1"));

        String message = tools.cancelExchangeRequest(5L, turn("turn-1"));

        verify(exchangeService, never()).cancelExchange(anyLong(), any());
        assertThat(message).contains("취소하지 않았습니다");
        // 막힌 뒤에도 확인 기록은 남아 있어서, 설명이 전달된 뒤 사용자가 다음 메시지로 동의하면 취소된다
        endTurn("turn-1");
        tools.cancelExchangeRequest(5L, turn("turn-2"));
        verify(exchangeService).cancelExchange(5L, USER_ID);
    }

    @Test
    void cancel_isRefusedWhenTheTurnThatExplainedItFailed() {
        // 확인 도구까지 불렀지만 AI 호출이 실패해서 사용자는 설명을 보지 못한 경우 (ChatService가 abortTurn)
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(myPending(5L)));
        tools.prepareCancelExchangeRequest(5L, turn("turn-1"));
        guard.abortTurn(USER_ID, "turn-1");

        // 다음 메시지로 "네"라고 해도 사용자가 설명을 받은 적이 없으니 취소되지 않는다
        String message = tools.cancelExchangeRequest(5L, turn("turn-2"));

        verify(exchangeService, never()).cancelExchange(anyLong(), any());
        assertThat(message).contains("취소하지 않았습니다");
    }

    @Test
    void cancel_confirmationWorksOnlyOnce() {
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(myPending(5L)));
        tools.prepareCancelExchangeRequest(5L, turn("turn-1"));
        endTurn("turn-1");
        tools.cancelExchangeRequest(5L, turn("turn-2"));

        String second = tools.cancelExchangeRequest(5L, turn("turn-3"));

        assertThat(second).contains("취소하지 않았습니다");
        verify(exchangeService).cancelExchange(5L, USER_ID);   // 한 번만 실행됐다
    }

    @Test
    void cancel_confirmationForOneRequestDoesNotCoverAnother() {
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(myPending(5L)));
        tools.prepareCancelExchangeRequest(5L, turn("turn-1"));
        endTurn("turn-1");

        tools.cancelExchangeRequest(6L, turn("turn-2"));   // 5번을 확인했는데 6번을 취소하려는 경우

        verify(exchangeService, never()).cancelExchange(anyLong(), any());
    }

    @Test
    void cancel_explainsExpectedFailuresInsteadOfThrowing() {
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(myPending(5L)));
        tools.prepareCancelExchangeRequest(5L, turn("turn-1"));
        endTurn("turn-1");
        doThrow(new ForbiddenException("교환 신청자만 교환 취소 할 수있습니다"))
            .when(exchangeService).cancelExchange(5L, USER_ID);

        String message = tools.cancelExchangeRequest(5L, turn("turn-2"));

        assertThat(message).startsWith("취소하지 못했습니다").contains("교환 신청자만");
        assertThat(notes).anyMatch(note -> note.contains("취소 실패"));
    }

    @Test
    void tools_workEvenWhenNoToolNotesListIsInTheContext() {
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of());
        ToolContext withoutNotes = new ToolContext(Map.of("userId", USER_ID, "turnId", "t1"));

        // 기록 목록이 없으면 기록만 건너뛰고 도구는 정상 동작한다
        assertThat(tools.getMyExchangeRequests(null, withoutNotes).requests()).isEmpty();
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
    void springAi_discoversAllThreeToolsAndHidesToolContextFromTheModel() {
        org.springframework.ai.tool.ToolCallback[] callbacks = org.springframework.ai.support.ToolCallbacks.from(tools);

        assertThat(callbacks).extracting(c -> c.getToolDefinition().name())
            .containsExactlyInAnyOrder("getMyExchangeRequests", "prepareCancelExchangeRequest", "cancelExchangeRequest");
        org.springframework.ai.tool.ToolCallback cancel = java.util.Arrays.stream(callbacks)
            .filter(c -> c.getToolDefinition().name().equals("cancelExchangeRequest")).findFirst().orElseThrow();
        // AI에게 보이는 입력은 신청 번호뿐이다. 로그인 사용자·대화 식별자(ToolContext)는 AI가 볼 수도 바꿀 수도 없다.
        assertThat(cancel.getToolDefinition().inputSchema()).contains("exchangeRequestId")
            .doesNotContain("userId").doesNotContain("turnId");
    }

    @Test
    void springAi_theListToolTakesAnOptionalCategoryTheModelCanChoose() {
        org.springframework.ai.tool.ToolCallback list = java.util.Arrays.stream(org.springframework.ai.support.ToolCallbacks.from(tools))
            .filter(c -> c.getToolDefinition().name().equals("getMyExchangeRequests")).findFirst().orElseThrow();

        String schema = list.getToolDefinition().inputSchema();

        // AI가 상태를 고를 수 있는 category가 보이고, 생략해도 되는 값이며, 로그인 사용자 정보는 보이지 않는다
        assertThat(schema).contains("category").doesNotContain("userId").doesNotContain("turnId");
        assertThat(schema).doesNotContain("\"required\":[\"category\"");
    }

    @Test
    void springAi_theCancelToolIgnoresAUserIdTheModelMakesUp() {
        when(exchangeService.getMyExchange(USER_ID)).thenReturn(List.of(myPending(5L)));
        tools.prepareCancelExchangeRequest(5L, turn("turn-1"));
        endTurn("turn-1");
        org.springframework.ai.tool.ToolCallback cancel = java.util.Arrays.stream(org.springframework.ai.support.ToolCallbacks.from(tools))
            .filter(c -> c.getToolDefinition().name().equals("cancelExchangeRequest")).findFirst().orElseThrow();

        // AI가 보내는 JSON 입력을 흉내 낸다. 그 안에 userId를 넣어도 쓰이지 않는다.
        String result = cancel.call("{\"exchangeRequestId\": 5, \"userId\": 999}", turn("turn-2"));

        verify(exchangeService).cancelExchange(5L, USER_ID);
        assertThat(result).contains("취소했습니다");
    }
}
