package com.goodsbridge.shop;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

// 가게 사장님 대시보드. 화면은 /shop/dashboard, 데이터는 /api/shop/dashboard (본인 가게 기준)
@Controller
@RequiredArgsConstructor
public class ShopDashboardController {

    private final ShopDashboardService dashboardService;
    private final ShopRepository shopRepository;

    @GetMapping("/shop/dashboard")
    public String dashboard(Model model) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        // 가게 사장님이 아니면 개인 마이페이지로 돌려보낸다 (다른 /shop/* 화면과 같은 규칙)
        boolean owner = userId != null && !shopRepository
            .findAllByOwnerUserIdAndDeletedFalse(userId, PageRequest.of(0, 1)).isEmpty();
        if (!owner) {
            return "redirect:/update";
        }
        model.addAttribute("userId", userId);
        return "shop/dashboard";
    }

    // 매출 창: 고른 해(year, 생략하면 올해)의 월별 매출과 종류별 내역
    @ResponseBody
    @GetMapping("/api/shop/dashboard/revenue")
    public ShopRevenueResponse revenue(@RequestParam(required = false) Integer year) {
        return dashboardService.getRevenueHistory(SecurityUtil.getCurrentUserId(), year);
    }

    @ResponseBody
    @GetMapping("/api/shop/dashboard")
    public ShopDashboardResponse data(@RequestParam(required = false) String month) {
        return dashboardService.getDashboard(SecurityUtil.getCurrentUserId(), month);
    }
}
