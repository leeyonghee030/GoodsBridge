package com.goodsbridge.notification;

import com.goodsbridge.user.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationResponseTest {

    private String urlOf(Notification.Type type, Notification.ReferenceType referenceType, Long referenceId) {
        Notification notification = new Notification(new User(), type, "제목", "내용", referenceType, referenceId);
        return new NotificationResponse.NotificationDto(notification).url();
    }

    @Test
    void exchangeNotificationOpensThatRequestInMyExchange() {
        // 내 교환 내역에서 그 신청의 팝업을 바로 연다 (목록에만 도착하면 어떤 신청인지 다시 찾아야 한다)
        assertEquals("/my/exchange?request=7", urlOf(Notification.Type.EXCHANGE_REQUEST, Notification.ReferenceType.EXCHANGE, 7L));
    }

    @Test
    void auctionNotificationOpensThatAuctionDetail() {
        // 낙찰 알림의 잔금 결제는 경매 상세에 있다
        assertEquals("/test/auction/5", urlOf(Notification.Type.AUCTION_WIN, Notification.ReferenceType.AUCTION, 5L));
    }

    @Test
    void deliveryAndPaymentNotificationsOpenMyPurchases() {
        // 그 구매의 단계 탭(배송 준비중 등)과 상세를 바로 연다
        assertEquals("/my/purchases?purchase=42", urlOf(Notification.Type.DELIVERY_STARTED, Notification.ReferenceType.DELIVERY, 42L));
        assertEquals("/my/purchases", urlOf(Notification.Type.PAYMENT_COMPLETED, Notification.ReferenceType.PAYMENT, 42L));
    }

    @Test
    void deliveryReferenceProducesPurchasesUrlInDto() {
        Notification notification = new Notification(
            new User(),
            Notification.Type.DELIVERY_STARTED,
            "배송 시작",
            "상품이 배송 중입니다.",
            Notification.ReferenceType.DELIVERY,
            42L
        );

        NotificationResponse.NotificationDto dto = new NotificationResponse.NotificationDto(notification);

        assertEquals("/my/purchases?purchase=42", dto.url());
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
