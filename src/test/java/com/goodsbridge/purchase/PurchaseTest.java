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
    void confirm_changesPaidPurchaseToConfirmedAfterShipping() {
        Purchase purchase = purchase();
        purchase.ship("123456789");

        purchase.confirm();

        assertEquals(PurchaseStatus.CONFIRMED, purchase.getStatus());
    }

    private Purchase purchase() {
        return Purchase.builder()
            .item(mock(Item.class))
            .user(mock(User.class))
            .paymentId("payment-1")
            .deliveryAddress("Seoul")
            .build();
    }
}
