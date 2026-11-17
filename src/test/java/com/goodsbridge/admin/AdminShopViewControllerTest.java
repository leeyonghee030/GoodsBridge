package com.goodsbridge.admin;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminShopViewControllerTest {
    private final AdminShopViewController controller = new AdminShopViewController();

    // 지정한 권한과 사용자 ID를 가진 인증 객체
    private Authentication authentication(String role, Long userId) {
        Authentication authentication = mock(Authentication.class);
        GrantedAuthority authority = () -> role;
        doReturn(List.of(authority)).when(authentication).getAuthorities();
        when(authentication.getPrincipal()).thenReturn(userId);
        return authentication;
    }

    @Test
    void shops_rendersShopsTemplateForAdmin() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.shops(authentication("ROLE_ADMIN", 7L), model);

        assertEquals("admin/shops", view);
        assertEquals(false, model.getAttribute("subscriptionsMode"));
        assertEquals(7L, model.getAttribute("userId"));
    }

    @Test
    void subscriptions_rendersSharedTemplateInSubscriptionsMode() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.subscriptions(authentication("ROLE_ADMIN", 7L), model);

        assertEquals("admin/shops", view);
        assertEquals(true, model.getAttribute("subscriptionsMode"));
    }

    @Test
    void subscriptionPayments_rendersPaymentsTemplateForAdmin() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.subscriptionPayments(authentication("ROLE_ADMIN", 7L), model);

        assertEquals("admin/subscription-payments", view);
    }

    @Test
    void nonAdmin_isShownForbiddenTemplate() {
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("admin/forbidden", controller.shops(authentication("ROLE_USER", 8L), model));
        assertEquals("admin/forbidden",
            controller.subscriptionPayments(authentication("ROLE_USER", 8L), new ExtendedModelMap()));
    }

    @Test
    void anonymous_isShownForbiddenTemplateWithoutUserId() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.shops(null, model);

        assertEquals("admin/forbidden", view);
        assertNull(model.getAttribute("userId"));
    }
}
