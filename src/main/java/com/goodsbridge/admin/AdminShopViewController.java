package com.goodsbridge.admin;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// 관리자: 입점 신청 승인 / 구독 현황 화면 (데이터는 기존 /api/admin/shops/*, /api/admin/subscriptions 를 사용)
@Controller
public class AdminShopViewController {

    // 입점 관리 (신청/승인된 가게/거절됨)
    @GetMapping("/test/admin/shops")
    public String shops(Authentication authentication, Model model) {
        return render(authentication, model, false);
    }

    // 구독 관리 (가게별 구독 기간/상태)
    @GetMapping("/test/admin/subscriptions")
    public String subscriptions(Authentication authentication, Model model) {
        return render(authentication, model, true);
    }

    private String render(Authentication authentication, Model model, boolean subscriptionsMode) {
        model.addAttribute("subscriptionsMode", subscriptionsMode);
        boolean admin = authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            model.addAttribute("userId", userId);
        }
        return admin ? "admin/shops" : "admin/forbidden";
    }
}
