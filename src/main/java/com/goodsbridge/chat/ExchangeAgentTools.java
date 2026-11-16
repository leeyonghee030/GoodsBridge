package com.goodsbridge.chat;

import com.goodsbridge._core.exception.CustomException;
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
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ExchangeAgentTools {

    private static final int MAX_LIST = 10;     // AI에게 한 번에 넘기는 신청 수 (너무 길면 답이 느려지고 비용이 든다)

    private final ExchangeService exchangeService;
    private final ChatLogService chatLogService;

    /** AI가 돌려받는 교환 신청 요약. 화면용 응답 전체 대신 AI가 답하는 데 필요한 것만 담는다. */
    public record ExchangeInfo(Long requestId, String status, String myRole, String postTitle,
                               String postItem, String proposedItem, String shop, String meetingAt) {
    }

    // [조회 도구] 읽기만 한다
    @Tool(description = "로그인한 사용자와 관련된 교환 신청 목록을 최근 순으로 최대 10건 조회한다. "
        + "사용자가 내 교환, 교환 신청 현황, 교환 진행 상태를 물을 때 사용한다.")
    public List<ExchangeInfo> getMyExchangeRequests(ToolContext toolContext) {
        Long userId = userId(toolContext);
        List<ExchangeInfo> result = exchangeService.getMyExchange(userId).stream()
            .sorted(Comparator.comparing(ExchangeRequestResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(MAX_LIST)
            .map(r -> toInfo(r, userId))
            .toList();
        chatLogService.save(userId, ChatRole.TOOL, "교환 신청 조회: " + result.size() + "건");
        return result;
    }

    // [수정 도구] 실제로 DB를 바꾼다 (대기 중인 신청 취소 + 보증금 환불)
    @Tool(description = "교환 신청 1건을 취소한다. 신청한 본인이 대기 중인 신청만 취소할 수 있고 보증금이 환불된다. "
        + "사용자가 취소를 분명히 요청했고 어떤 신청(번호)인지 확인된 경우에만 호출한다. 되돌릴 수 없다.")
    public String cancelExchangeRequest(
        @ToolParam(description = "취소할 교환 신청 번호 (getMyExchangeRequests 결과의 requestId)") Long exchangeRequestId,
        ToolContext toolContext) {
        Long userId = userId(toolContext);
        try {
            exchangeService.cancelExchange(exchangeRequestId, userId);
            chatLogService.save(userId, ChatRole.TOOL, "교환 신청 " + exchangeRequestId + "번 취소");
            return "교환 신청 " + exchangeRequestId + "번을 취소했습니다. 보증금은 환불됩니다.";
        } catch (CustomException e) {
            // 권한 없음·이미 처리됨 같은 예상 가능한 실패는 AI가 사용자에게 설명할 수 있게 문장으로 돌려준다
            chatLogService.save(userId, ChatRole.TOOL, "교환 신청 " + exchangeRequestId + "번 취소 실패: " + e.getMessage());
            return "취소하지 못했습니다: " + e.getMessage();
        }
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

    // 서버가 넣어 준 로그인 사용자 id를 꺼낸다 (ChatService의 toolContext 참고)
    private Long userId(ToolContext toolContext) {
        Object id = toolContext.getContext().get(ChatService.USER_ID_KEY);
        if (!(id instanceof Long userId)) {
            throw new IllegalStateException("도구를 실행할 사용자 정보가 없습니다.");
        }
        return userId;
    }
}
