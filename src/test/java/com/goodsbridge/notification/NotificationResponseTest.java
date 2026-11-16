package com.goodsbridge.notification;

import com.goodsbridge.user.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationResponseTest {

    @Test
    void deliveryReferenceWithoutPathProducesNullUrlInDto() {
        Notification notification = new Notification(
            new User(),
            Notification.Type.DELIVERY_STARTED,
            "배송 시작",
            "상품이 배송 중입니다.",
            Notification.ReferenceType.DELIVERY,
            42L
        );

        NotificationResponse.NotificationDto dto = new NotificationResponse.NotificationDto(notification);

        assertNull(dto.url());
    }

    @Test
    void idBasedReferenceProducesUrlInDto() {
        Notification notification = new Notification(
            new User(),
            Notification.Type.EXCHANGE_REQUEST,
            "새 교환 신청",
            "교환 신청이 도착했습니다.",
            Notification.ReferenceType.EXCHANGE_POST,
            42L
        );

        NotificationResponse.NotificationDto dto = new NotificationResponse.NotificationDto(notification);

        assertEquals("/exchange/42", dto.url());
    }

    @Test
    void missingReferenceTypeProducesNullUrlInDto() {
        Notification notification = new Notification(
            new User(),
            Notification.Type.DELIVERY_STARTED,
            "배송 시작",
            "상품이 배송 중입니다.",
            null,
            42L
        );

        NotificationResponse.NotificationDto dto = new NotificationResponse.NotificationDto(notification);

        assertNull(dto.url());
    }
}
