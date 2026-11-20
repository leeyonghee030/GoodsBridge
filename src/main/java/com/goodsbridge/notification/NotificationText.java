package com.goodsbridge.notification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 알림 문구에 넣는 날짜·금액 표기를 한 가지로 맞춘다.
 * (LocalDateTime 을 그대로 붙이면 "2026-11-20T15:00:00.123" 처럼, 금액을 그대로 붙이면 "15000원" 처럼 보인다)
 */
public final class NotificationText {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private NotificationText() {
    }

    // 예: 11월 20일 15:00
    public static String when(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(WHEN);
    }

    // 예: 15,000원
    public static String won(Integer amount) {
        return amount == null ? "" : won(amount.longValue());
    }

    public static String won(long amount) {
        return String.format("%,d원", amount);
    }
}
