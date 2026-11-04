package com.goodsbridge.purchase;

import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.payment.PaymentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock PurchaseRepository purchaseRepository;
    @Mock PaymentService paymentService;
    @Mock EntityManager entityManager;
    @Mock ItemRepository itemRepository;
    @InjectMocks PurchaseService purchaseService;

    @Test
    void findMine_returnsPurchaseDetailsFromRepository() {
        Item item = mock(Item.class);
        when(item.getId()).thenReturn(7L);
        Purchase purchase = Purchase.builder()
            .item(item)
            .paymentId("payment-1")
            .deliveryAddress("Seoul")
            .build();
        when(purchaseRepository.findAllPurchaseByUserId(3L)).thenReturn(List.of(purchase));

        List<PurchaseResponse.DetailDto> result = purchaseService.findMine(3L);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getItemId());
        assertEquals("payment-1", result.get(0).getPaymentId());
        verify(purchaseRepository).findAllPurchaseByUserId(3L);
    }
}
