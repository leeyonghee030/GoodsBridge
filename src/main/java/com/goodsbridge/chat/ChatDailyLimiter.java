package com.goodsbridge.chat;

import com.goodsbridge._core.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자별 AI 질문 횟수 제한 (기본 하루 {@value #DEFAULT_LIMIT}번). 무료 AI 한도를 아끼기 위한 장치다.
 *
 * <p>[세는 것] 실제로 AI를 부른 질문만 센다. 빠른 메뉴 버튼은 AI를 쓰지 않으니 제한이 없고,
 * 질문 필터가 막은 요청과 AI 호출이 실패한 요청({@link #release})도 세지 않는다.
 * 하루의 기준은 서버의 날짜(자정)다. "새 대화"로 기록을 지워도 횟수는 그대로다 (기록과 따로 센다).
 *
 * <p>서버 메모리에서만 센다 (이 프로젝트는 Redis를 쓰지 않는다). 서버를 다시 켜면 처음부터 센다.
 * 사용자마다 (날짜, 횟수) 하나만 들고 있어서, 날짜가 바뀌면 덮어쓰므로 쌓이지 않는다.
 */
@Component
class ChatDailyLimiter {

    static final int DEFAULT_LIMIT = 3;

    static final String LIMIT_MESSAGE = "오늘 AI 질문 한도를 모두 썼어요. 내일 다시 쓸 수 있어요. 대신 아래 버튼으로 같은 기능을 쓸 수 있어요.";

    private record Usage(LocalDate date, int count) {
    }

    private final int limit;
    private final Map<Long, Usage> usages = new ConcurrentHashMap<>();

    ChatDailyLimiter(@Value("${app.chat.daily-limit:" + DEFAULT_LIMIT + "}") int limit) {
        this.limit = limit;
    }

    int limit() {
        return limit;
    }

    /** 오늘 남은 질문 횟수. */
    int remaining(Long userId) {
        Usage usage = usages.get(userId);
        int used = usage != null && usage.date().equals(LocalDate.now()) ? usage.count() : 0;
        return Math.max(0, limit - used);
    }

    /** AI를 부르기 전에 한 번 쓴 것으로 먼저 센다 (동시에 여러 번 보내도 한도를 넘지 못하게). 한도를 넘으면 예외. */
    void reserve(Long userId) {
        LocalDate today = LocalDate.now();
        boolean[] allowed = {false};
        usages.compute(userId, (id, usage) -> {
            int used = usage != null && usage.date().equals(today) ? usage.count() : 0;
            if (used >= limit) {
                return usage;
            }
            allowed[0] = true;
            return new Usage(today, used + 1);
        });
        if (!allowed[0]) {
            throw new ServiceUnavailableException(LIMIT_MESSAGE);
        }
    }

    /** AI 호출이 실패했거나 답을 못 받았으면 미리 센 횟수를 돌려준다 (사용자 잘못이 아니다). */
    void release(Long userId) {
        LocalDate today = LocalDate.now();
        usages.computeIfPresent(userId, (id, usage) ->
            usage.date().equals(today) && usage.count() > 0 ? new Usage(today, usage.count() - 1) : usage);
    }
}
