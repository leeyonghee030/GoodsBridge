package com.goodsbridge.notification;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationTextTest {

    @Test
    void when_showsMonthDayAndTime() {
        assertEquals("11월 20일 15:05", NotificationText.when(LocalDateTime.of(2026, 11, 20, 15, 5, 42, 123)));
        assertEquals("", NotificationText.when(null));
    }

    @Test
    void won_addsThousandsSeparator() {
        assertEquals("15,000원", NotificationText.won(15000));
        assertEquals("0원", NotificationText.won(0));
        assertEquals("", NotificationText.won(null));
    }
}
