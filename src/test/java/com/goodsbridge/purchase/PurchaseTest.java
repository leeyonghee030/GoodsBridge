package com.goodsbridge.purchase;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.item.Item;
import com.goodsbridge.user.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PurchaseTest {

    @Test
    void ship_setsTrackingNumberAndShippedStatus() {
        Purchase purchase = purchase();

        purchase.ship("123456789");

        assertEquals("123456789", purchase.getTrackingNumber());
        assertEquals(DeliveryStatus.SHIPPED, purchase.getDeliveryStatus());
    }

    @Test
    void ship_rejectsBlankTrackingNumber() {
        Purchase purchase = purchase();

        assertThrows(BadRequestException.class, () -> purchase.ship(" "));
        assertEquals(DeliveryStatus.PREPARING, purchase.getDeliveryStatus());
    }

    @Test
    void markDelivered_requiresShippedStatus() {
        Purchase purchase = purchase();

        assertThrows(BadRequestException.class, purchase::markDelivered);

        purchase.ship("123456789");
        purchase.markDelivered();

        assertEquals(DeliveryStatus.DELIVERED, purchase.getDeliveryStatus());
    }

    @Test
    void confirm_changesPaidPurchaseToConfirmedAfterDelivered() {
        Purchase purchase = purchase();
        purchase.ship("123456789");
        purchase.markDelivered();

        purchase.confirm();

        assertEquals(PurchaseStatus.CONFIRMED, purchase.getStatus());
    }

    @Test
    void confirm_isRejectedBeforeDelivered() {
        Purchase preparing = purchase();
        assertThrows(BadRequestException.class, preparing::confirm);

        Purchase shipped = purchase();
        shipped.ship("123456789");
        assertThrows(BadRequestException.class, shipped::confirm);
        // 거절된 뒤에도 판매자는 배송 완료 처리를 할 수 있다.
        shipped.markDelivered();
        assertEquals(DeliveryStatus.DELIVERED, shipped.getDeliveryStatus());
    }

    private Purchase purchase() {
        return Purchase.builder()
            .item(mock(Item.class))
            .user(mock(User.class))
            .paymentId("payment-1")
            .deliveryAddress("Seoul")
            .build();
    }


    @Test
    void ship_allowsCorrectingTrackingNumberWhileShipped() {
        Purchase purchase = purchase();
        purchase.ship("WRONG-1");

        purchase.ship("RIGHT-1");

        assertEquals("RIGHT-1", purchase.getTrackingNumber());
        assertEquals(DeliveryStatus.SHIPPED, purchase.getDeliveryStatus());
    }

    @Test
    void ship_cannotRevertDeliveredPurchaseBackToShipped() {
        Purchase purchase = purchase();
        purchase.ship("TRACK-1");
        purchase.markDelivered();

        assertThrows(BadRequestException.class, () -> purchase.ship("TRACK-2"));

        assertEquals(DeliveryStatus.DELIVERED, purchase.getDeliveryStatus());
        assertEquals("TRACK-1", purchase.getTrackingNumber());
    }

    @Test
    void ship_trimsTrackingNumberAndRejectsTooLongValue() {
        Purchase purchase = purchase();

        assertThrows(BadRequestException.class, () -> purchase.ship("1".repeat(51)));
        assertEquals(DeliveryStatus.PREPARING, purchase.getDeliveryStatus());

        purchase.ship("  123456789  ");
        assertEquals("123456789", purchase.getTrackingNumber());
    }
}
