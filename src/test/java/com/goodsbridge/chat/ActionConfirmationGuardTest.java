package com.goodsbridge.chat;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ActionConfirmationGuardTest {

    // 시간을 마음대로 움직일 수 있는 시계
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-16T10:00:00Z");

        void plusMinutes(long minutes) {
            now = now.plusSeconds(minutes * 60);
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final ActionConfirmationGuard guard = new ActionConfirmationGuard(clock);

    @Test
    void consume_isFalseWhenNothingWasPrepared() {
        assertThat(guard.consume(1L, "cancel", 5L, "t2")).isFalse();
    }

    @Test
    void consume_isFalseInTheSameTurnAndTrueInALaterOne() {
        guard.markPrepared(1L, "cancel", 5L, "t1");

        assertThat(guard.consume(1L, "cancel", 5L, "t1")).isFalse();
        guard.completeTurn(1L, "t1");
        assertThat(guard.consume(1L, "cancel", 5L, "t1")).isFalse();    // 같은 턴이면 끝난 뒤에도 안 된다
        assertThat(guard.consume(1L, "cancel", 5L, "t2")).isTrue();
    }

    @Test
    void consume_isFalseUntilTheTurnThatExplainedItCompletes() {
        guard.markPrepared(1L, "cancel", 5L, "t1");

        // 설명을 전달하던 턴이 끝나기 전(또는 실패한 채)에는 다음 턴에서 동의해도 실행되지 않는다
        assertThat(guard.consume(1L, "cancel", 5L, "t2")).isFalse();
        guard.completeTurn(1L, "t1");
        assertThat(guard.consume(1L, "cancel", 5L, "t2")).isTrue();
    }

    @Test
    void abortTurn_discardsThatTurnsPreparationsOnly() {
        guard.markPrepared(1L, "cancel", 5L, "t1");
        guard.markPrepared(1L, "cancel", 6L, "t2");
        guard.markPrepared(2L, "cancel", 5L, "t1");     // 다른 사용자의 같은 turnId는 건드리지 않는다
        guard.completeTurn(1L, "t2");
        guard.completeTurn(2L, "t1");

        guard.abortTurn(1L, "t1");

        guard.completeTurn(1L, "t1");   // 이미 버려져서 아무 일도 없다
        assertThat(guard.consume(1L, "cancel", 5L, "t3")).isFalse();     // 버려진 확인
        assertThat(guard.consume(1L, "cancel", 6L, "t3")).isTrue();      // 다른 턴의 확인은 유지
        assertThat(guard.consume(2L, "cancel", 5L, "t3")).isTrue();      // 다른 사용자의 확인은 유지
    }

    @Test
    void consume_worksOnlyOnce() {
        guard.markPrepared(1L, "cancel", 5L, "t1");
        guard.completeTurn(1L, "t1");

        assertThat(guard.consume(1L, "cancel", 5L, "t2")).isTrue();
        assertThat(guard.consume(1L, "cancel", 5L, "t3")).isFalse();
    }

    @Test
    void consume_doesNotMixUsersActionsOrTargets() {
        guard.markPrepared(1L, "cancel", 5L, "t1");
        guard.completeTurn(1L, "t1");

        assertThat(guard.consume(2L, "cancel", 5L, "t2")).isFalse();     // 다른 사용자
        assertThat(guard.consume(1L, "update", 5L, "t2")).isFalse();     // 다른 동작
        assertThat(guard.consume(1L, "cancel", 6L, "t2")).isFalse();     // 다른 대상
        assertThat(guard.consume(1L, "cancel", 5L, "t2")).isTrue();      // 원래 것은 그대로 유효
    }

    @Test
    void consume_expiresAfterTenMinutes() {
        guard.markPrepared(1L, "cancel", 5L, "t1");
        guard.completeTurn(1L, "t1");

        clock.plusMinutes(11);

        assertThat(guard.consume(1L, "cancel", 5L, "t2")).isFalse();
    }

    @Test
    void consume_stillValidJustBeforeTheLimit() {
        guard.markPrepared(1L, "cancel", 5L, "t1");
        guard.completeTurn(1L, "t1");

        clock.plusMinutes(9);

        assertThat(guard.consume(1L, "cancel", 5L, "t2")).isTrue();
    }

    @Test
    void clearUser_discardsOnlyThatUsersPreparations() {
        guard.markPrepared(1L, "cancel", 5L, "t1");
        guard.markPrepared(11L, "cancel", 5L, "t1");     // 앞자리가 같아도 다른 사용자(11)는 건드리지 않는다
        guard.completeTurn(1L, "t1");
        guard.completeTurn(11L, "t1");

        guard.clearUser(1L);

        assertThat(guard.consume(1L, "cancel", 5L, "t2")).isFalse();
        assertThat(guard.consume(11L, "cancel", 5L, "t2")).isTrue();
    }
}
