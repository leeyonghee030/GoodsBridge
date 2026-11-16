package com.goodsbridge.chat;

import com.goodsbridge._core.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자별 채팅 요청 횟수 제한 (1분에 {@value #MAX_PER_MINUTE}번).
 *
 * <p>무료 AI 한도를 한 사람이 빠르게 다 쓰거나, 자동 프로그램이 계속 호출하는 것을 막는다.
 * 서버 한 대 메모리에서만 세는 단순한 방식이다 (이 프로젝트는 Redis를 쓰지 않는다).
 */
@Component
class ChatRateLimiter {

    static final int MAX_PER_MINUTE = 10;
    private static final long WINDOW_MILLIS = 60_000;

    private final Map<Long, Deque<Long>> requests = new ConcurrentHashMap<>();

    void check(Long userId) {
        long now = System.currentTimeMillis();
        Deque<Long> times = requests.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MILLIS) {
                times.pollFirst();   // 1분이 지난 기록은 버린다
            }
            if (times.size() >= MAX_PER_MINUTE) {
                throw new BadRequestException("메시지를 너무 빨리 보내고 있어요. 잠시 후 다시 시도해 주세요.");
            }
            times.addLast(now);
        }
    }
}
