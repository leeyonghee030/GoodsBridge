package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 입점 신청의 체인점 코드 검증, 승인 취소(reject) 시 구독/상품 정리를 검증한다 (DB 없이 Mockito 로)
@ExtendWith(MockitoExtension.class)
class ShopApprovalAndChainCodeTest {

    private static final String CODE = "GB-CHAIN-TEST";
    private static final Long SHOP_ID = 5L;
    private static final Long ADMIN_ID = 9L;

    @Mock ShopRepository shopRepository;
    @Mock UserRepository userRepository;
    @Mock ItemRepository itemRepository;
    @Mock ShopSubscriptionRepository subscriptionRepository;
    @InjectMocks ShopService shopService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(shopService, "chainCode", CODE);
    }

    // ===== 체인점 코드 =====

    @Test
    void create_savesPendingShopWhenChainCodeMatches() {
        when(shopRepository.save(any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShopResponse response = shopService.create(1L, request("  " + CODE + "  "));   // 앞뒤 공백은 무시

        assertEquals(ShopStatus.PENDING, response.getStatus());
        assertEquals(1L, response.getOwnerUserId());
        verify(shopRepository).save(any(Shop.class));
    }

    @Test
    void create_rejectsWrongChainCode() {
        assertThrows(BadRequestException.class, () -> shopService.create(1L, request("WRONG")));
        verify(shopRepository, never()).save(any());
    }

    @Test
    void create_rejectsMissingChainCode() {
        assertThrows(BadRequestException.class, () -> shopService.create(1L, request(null)));
        verify(shopRepository, never()).save(any());
    }

    // ===== 승인 취소 =====

    @Test
    void reject_approvedShop_closesOnSaleItemsAndCancelsActiveSubscription() {
        Shop shop = shop(ShopStatus.APPROVED);
        ShopSubscription subscription = mock(ShopSubscription.class);
        givenAdminAndShop(shop);
        when(subscriptionRepository.findActiveByShopIdWithLock(SHOP_ID)).thenReturn(Optional.of(subscription));

        shopService.reject(ADMIN_ID, SHOP_ID);

        assertEquals(ShopStatus.REJECTED, shop.getStatus());
        // 판매중 상품만 닫는 메서드를 써야 판매완료 상품은 그대로 남는다 (전체 삭제 메서드를 쓰면 안 됨)
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(itemRepository).closeOnSaleByShopId(eq(SHOP_ID), now.capture());
        verify(itemRepository, never()).softDeleteByShopId(any(), any());
        verify(subscription).delete();
    }

    @Test
    void reject_pendingShop_doesNotTouchItemsOrSubscription() {
        Shop shop = shop(ShopStatus.PENDING);
        givenAdminAndShop(shop);

        shopService.reject(ADMIN_ID, SHOP_ID);

        assertEquals(ShopStatus.REJECTED, shop.getStatus());
        verifyNoInteractions(itemRepository);
        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void reject_requiresAdmin() {
        User user = mock(User.class);
        when(user.getRole()).thenReturn(Role.USER);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class, () -> shopService.reject(ADMIN_ID, SHOP_ID));
        verifyNoInteractions(itemRepository);
    }

    private void givenAdminAndShop(Shop shop) {
        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(shopRepository.findById(SHOP_ID)).thenReturn(Optional.of(shop));
    }

    private static Shop shop(ShopStatus status) {
        Shop shop = Shop.builder().ownerUserId(1L).name("테스트점").region("서울").address("마포구 1").status(status).build();
        ReflectionTestUtils.setField(shop, "id", SHOP_ID);
        return shop;
    }

    private static ShopCreateRequest request(String chainCode) {
        return ShopCreateRequest.builder().name("테스트점").region("서울").address("마포구 1").chainCode(chainCode).build();
    }
}
