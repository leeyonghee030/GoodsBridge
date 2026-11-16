package com.goodsbridge.chat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 취소·수정처럼 되돌릴 수 없는 동작을 AI가 혼자 실행하지 못하게 막는 "이중 확인" 장치.
 *
 * <p>[왜 필요한가] 시스템 프롬프트에 "확인한 뒤 실행하라"고 적어도 AI가 건너뛸 수 있다.
 * 그래서 규칙을 서버 코드로 강제한다.
 * <ol>
 *   <li>1단계(확인): AI가 확인 도구를 부르면 그 동작을 "확인 대기"로 기록한다. 사용자에게는 결과와 영향을 설명해야 한다.</li>
 *   <li>턴 종료: 그 대화 턴이 <b>설명을 사용자에게 무사히 전달하고 끝났을 때만</b> {@link #completeTurn}으로 확인을 인정한다.
 *       AI 호출이 중간에 실패했다면 {@link #abortTurn}으로 버린다. (사용자가 보지도 못한 설명에 "네"로 취소되는 일을 막는다)</li>
 *   <li>2단계(실행): 실행 도구는 <b>앞선 대화 턴에서 확인이 인정된 동작만</b> 실행한다.
 *       확인과 실행이 같은 턴 안에서 일어났다면 사용자가 답할 기회가 없었다는 뜻이므로 거절한다.</li>
 * </ol>
 * 확인은 한 번만 쓸 수 있고(실행하면 사라진다) {@value #TTL_MINUTES}분이 지나면 무효가 된다.
 *
 * <p>※ 서버 메모리에 보관한다. 서버를 재시작하면 확인 대기가 사라지고(다시 확인하면 된다),
 * 서버를 여러 대로 늘리면 Redis 같은 공용 저장소로 옮겨야 한다.
 */
@Component
public class ActionConfirmationGuard {

    static final long TTL_MINUTES = 10;

    // delivered: 설명이 사용자에게 전달된 턴인지 (completeTurn 이후 true)
    private record Prepared(String turnId, Instant at, boolean delivered) {
    }

    private final Clock clock;
    private final Map<String, Prepared> prepared = new ConcurrentHashMap<>();

    @Autowired
    public ActionConfirmationGuard() {
        this(Clock.systemDefaultZone());
    }

    ActionConfirmationGuard(Clock clock) {   // 테스트에서 시간을 조작하려고 따로 둔 생성자
        this.clock = clock;
    }

    /** 1단계: 이 사용자의 이 동작을 "확인 대기"로 기록한다. turnId는 지금 진행 중인 대화 한 번의 식별자다. */
    public void markPrepared(Long userId, String action, Long targetId, String turnId) {
        purgeExpired();
        prepared.put(key(userId, action, targetId), new Prepared(turnId, clock.instant(), false));
    }

    /** 턴 종료(성공): 이 턴에서 기록한 확인을 "사용자에게 전달됨"으로 인정한다. 이후 턴에서 동의하면 실행할 수 있다. */
    public void completeTurn(Long userId, String turnId) {
        prepared.replaceAll((k, p) -> k.startsWith(userId + ":") && p.turnId().equals(turnId)
            ? new Prepared(p.turnId(), p.at(), true) : p);
    }

    /** 턴 종료(실패): 설명이 전달되지 않았을 수 있으니 이 턴에서 기록한 확인을 버린다. */
    public void abortTurn(Long userId, String turnId) {
        prepared.entrySet().removeIf(e -> e.getKey().startsWith(userId + ":") && e.getValue().turnId().equals(turnId));
    }

    /**
     * 2단계: 실행해도 되는지 확인하고 확인 기록을 지운다(한 번만 유효).
     * 이전 대화 턴에서 확인이 인정(completeTurn)된 동작이고 유효시간 안일 때만 true.
     */
    public boolean consume(Long userId, String action, Long targetId, String turnId) {
        purgeExpired();
        String key = key(userId, action, targetId);
        Prepared found = prepared.get(key);
        // 같은 턴에서 막힌 경우에는 확인 기록을 남겨 둔다. 사용자가 다음 메시지로 동의하면 그때 실행할 수 있어야 한다.
        if (found == null || !found.delivered() || found.turnId().equals(turnId)) {
            return false;
        }
        prepared.remove(key);
        return true;
    }

    private void purgeExpired() {
        Instant limit = clock.instant().minus(Duration.ofMinutes(TTL_MINUTES));
        prepared.values().removeIf(p -> p.at().isBefore(limit));
    }

    private String key(Long userId, String action, Long targetId) {
        return userId + ":" + action + ":" + targetId;
    }
}
