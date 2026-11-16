package com.goodsbridge.chat;

import com.goodsbridge._core.exception.CustomException;
import com.goodsbridge.exchange.ExchangeRequestStatus;
import com.goodsbridge.exchange.ExchangeService;
import com.goodsbridge.exchange.dto.ExchangeRequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * AI가 호출할 수 있는 "도구(Tool)" 모음 - 교환 편.
 *
 * <p>[공부 포인트]
 * <ul>
 *   <li>{@code @Tool}이 붙은 메서드를 AI(Gemini)가 "함수"로 인식한다. description을 읽고 언제 부를지 AI가 스스로 정한다.</li>
 *   <li>이 메서드는 REST 엔드포인트가 아니다. 서비스 메서드를 직접 부른다. (이슈 #13: /api/chat 하나만 외부에 노출)</li>
 *   <li><b>로그인한 사용자 id는 AI가 넘기는 값이 아니라 서버가 {@link ToolContext}로 넣어 준다.</b>
 *       AI가 다른 사람 id를 지어내서 남의 신청을 취소하는 일을 막는다.</li>
 *   <li><b>조회는 바로 답하지만, 취소처럼 되돌릴 수 없는 동작은 두 단계</b>다:
 *       {@link #prepareCancelExchangeRequest 확인 도구} → 사용자에게 설명하고 동의받기 → {@link #cancelExchangeRequest 실행 도구}.
 *       이 순서는 프롬프트가 아니라 {@link ActionConfirmationGuard}가 서버에서 강제한다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ExchangeAgentTools {

    static final String ACTION_CANCEL = "cancelExchange";   // ActionConfirmationGuard에서 동작을 구분하는 이름
    private static final int MAX_LIST = 10;     // AI에게 한 번에 넘기는 신청 수 (너무 길면 답이 느려지고 비용이 든다)

    private final ExchangeService exchangeService;
    private final ActionConfirmationGuard confirmationGuard;

    /** AI가 돌려받는 교환 신청 요약. 화면용 응답 전체 대신 AI가 답하는 데 필요한 것만 담는다. */
    public record ExchangeInfo(Long requestId, String status, String myRole, String postTitle,
                               String postItem, String proposedItem, String shop, String meetingAt) {
    }

    /** 취소 확인 결과. AI는 이 내용을 사용자에게 그대로 설명하고 동의를 받아야 한다. */
    public record CancelPreview(Long requestId, boolean cancellable, String problem, String postTitle,
                                String postItem, String proposedItem, String status, Integer refundAmount,
                                String whatHappens, String nextStep) {
    }

    // [조회 도구] 읽기만 한다. 바로 답해도 된다.
    @Tool(description = "로그인한 사용자와 관련된 교환 신청 목록을 최근 순으로 최대 10건 조회한다. "
        + "사용자가 내 교환, 교환 신청 현황, 교환 진행 상태를 물을 때 사용한다.")
    public List<ExchangeInfo> getMyExchangeRequests(ToolContext toolContext) {
        Long userId = userId(toolContext);
        List<ExchangeInfo> result = exchangeService.getMyExchange(userId).stream()
            .sorted(Comparator.comparing(ExchangeRequestResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(MAX_LIST)
            .map(r -> toInfo(r, userId))
            .toList();
        note(toolContext, "교환 신청 조회: " + result.size() + "건");
        return result;
    }

    // [1단계: 확인 도구] 취소해도 되는지 점검하고, 취소하면 어떻게 되는지 알려 준다. DB는 바꾸지 않는다.
    @Tool(description = "교환 신청을 취소하기 전에 반드시 먼저 호출하는 확인 도구. 취소할 수 있는지 점검하고 취소하면 어떻게 되는지 알려 준다. "
        + "아무것도 취소하지 않는다. 결과를 사용자에게 쉽게 설명하고 '정말 취소할까요?'라고 물어 동의를 받은 뒤에만 cancelExchangeRequest를 호출한다.")
    public CancelPreview prepareCancelExchangeRequest(
        @ToolParam(description = "취소하려는 교환 신청 번호 (getMyExchangeRequests 결과의 requestId)") Long exchangeRequestId,
        ToolContext toolContext) {
        Long userId = userId(toolContext);

        ExchangeRequestResponse request = exchangeService.getMyExchange(userId).stream()
            .filter(r -> exchangeRequestId.equals(r.getId()))
            .findFirst().orElse(null);
        if (request == null) {
            return rejected(exchangeRequestId, null, "내 교환 신청 목록에 없는 번호입니다.");
        }
        if (!userId.equals(request.getProposerUserId())) {
            return rejected(exchangeRequestId, request, "교환을 신청한 본인만 취소할 수 있습니다. 내 게시글에 들어온 신청은 '거절'해야 합니다.");
        }
        if (request.getStatus() != ExchangeRequestStatus.PENDING) {
            return rejected(exchangeRequestId, request, "대기 중인 신청만 취소할 수 있습니다. 현재 상태: " + request.getStatus());
        }

        // 점검 통과: 이 동작을 "확인 대기"로 기록한다. 사용자가 다음 메시지로 동의해야 실행 도구가 통과된다.
        confirmationGuard.markPrepared(userId, ACTION_CANCEL, exchangeRequestId, turnId(toolContext));
        note(toolContext, "교환 신청 " + exchangeRequestId + "번 취소 확인 (사용자 동의 대기)");
        int refund = request.getDepositAmountB() == null ? 0 : request.getDepositAmountB();
        return new CancelPreview(exchangeRequestId, true, null, request.getExchangeItemTitle(),
            request.getItemTitle(), request.getItemBTitle(), String.valueOf(request.getStatus()), refund,
            "취소하면 신청 상태가 '취소됨'으로 바뀌고 낸 보증금 " + refund + "원이 환불됩니다. "
                + "게시글 주인에게 취소 알림이 가고, 한 번 취소하면 되돌릴 수 없으며 다시 하려면 새로 신청하고 보증금을 다시 내야 합니다.",
            "위 내용을 사용자에게 설명하고 '정말 취소할까요?'라고 물어보세요. 사용자가 다음 메시지에서 분명히 동의하면 cancelExchangeRequest를 호출하세요.");
    }

    // [2단계: 실행 도구] 실제로 DB를 바꾼다 (대기 중인 신청 취소 + 보증금 환불). 확인과 사용자 동의가 있어야만 실행된다.
    @Tool(description = "교환 신청 1건을 실제로 취소하고 보증금을 환불한다. 되돌릴 수 없다. "
        + "prepareCancelExchangeRequest로 확인하고 그 내용을 사용자에게 설명한 뒤, 사용자가 다음 메시지에서 분명히 동의했을 때만 호출한다. "
        + "확인 없이 호출하면 취소되지 않는다.")
    public String cancelExchangeRequest(
        @ToolParam(description = "취소할 교환 신청 번호 (prepareCancelExchangeRequest로 확인한 번호)") Long exchangeRequestId,
        ToolContext toolContext) {
        Long userId = userId(toolContext);

        // 서버가 강제하는 이중 확인: 앞선 대화에서 확인된 동작만 실행한다
        if (!confirmationGuard.consume(userId, ACTION_CANCEL, exchangeRequestId, turnId(toolContext))) {
            note(toolContext, "교환 신청 " + exchangeRequestId + "번 취소 거절: 확인·동의 절차 없음");
            return "취소하지 않았습니다. 취소 전에 prepareCancelExchangeRequest로 확인하고, 그 내용을 사용자에게 설명해 "
                + "동의를 받은 뒤(사용자의 다음 메시지에서)에만 취소할 수 있습니다. 지금 사용자에게 취소 내용을 설명하고 동의를 구하세요.";
        }
        try {
            exchangeService.cancelExchange(exchangeRequestId, userId);
            note(toolContext, "교환 신청 " + exchangeRequestId + "번 취소");
            return "교환 신청 " + exchangeRequestId + "번을 취소했습니다. 보증금은 환불됩니다.";
        } catch (CustomException e) {
            // 권한 없음·이미 처리됨 같은 예상 가능한 실패는 AI가 사용자에게 설명할 수 있게 문장으로 돌려준다
            note(toolContext, "교환 신청 " + exchangeRequestId + "번 취소 실패: " + e.getMessage());
            return "취소하지 못했습니다: " + e.getMessage();
        }
    }

    private CancelPreview rejected(Long requestId, ExchangeRequestResponse r, String problem) {
        return new CancelPreview(requestId, false, problem,
            r == null ? null : r.getExchangeItemTitle(), r == null ? null : r.getItemTitle(),
            r == null ? null : r.getItemBTitle(), r == null ? null : String.valueOf(r.getStatus()), null,
            "취소할 수 없습니다.", "이유를 사용자에게 쉽게 설명하세요. 취소를 실행하지 마세요.");
    }

    private ExchangeInfo toInfo(ExchangeRequestResponse r, Long userId) {
        boolean proposer = userId.equals(r.getProposerUserId());
        return new ExchangeInfo(
            r.getId(),
            String.valueOf(r.getStatus()),
            proposer ? "신청자" : "게시글 주인",
            r.getExchangeItemTitle(),
            r.getItemTitle(),
            r.getItemBTitle(),
            r.getShopName(),
            r.getMeetingAt() == null ? null : r.getMeetingAt().toString());
    }

    // 도구 실행 기록을 이번 턴의 목록에 적는다. 바로 저장하지 않는 이유: AI가 답을 못 하면 이 턴의 기록은 남기지 않으려고,
    // ChatService가 답을 받았을 때 질문·답변과 함께 저장한다. (환불·취소 자체는 교환 내역에 따로 남는다)
    @SuppressWarnings("unchecked")
    private void note(ToolContext toolContext, String text) {
        Object notes = toolContext.getContext().get(ChatService.TOOL_NOTES_KEY);
        if (notes instanceof List<?> list) {
            ((List<String>) list).add(text);
        }
    }

    // 서버가 넣어 준 로그인 사용자 id를 꺼낸다 (ChatService의 toolContext 참고)
    private Long userId(ToolContext toolContext) {
        Object id = toolContext.getContext().get(ChatService.USER_ID_KEY);
        if (!(id instanceof Long userId)) {
            throw new IllegalStateException("도구를 실행할 사용자 정보가 없습니다.");
        }
        return userId;
    }

    // 지금 진행 중인 대화 한 번(요청 하나)의 식별자. 확인과 실행이 같은 턴인지 가리는 데 쓴다.
    private String turnId(ToolContext toolContext) {
        Object id = toolContext.getContext().get(ChatService.TURN_ID_KEY);
        if (!(id instanceof String turnId)) {
            throw new IllegalStateException("대화 정보가 없습니다.");
        }
        return turnId;
    }
}
