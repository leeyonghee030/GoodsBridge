package com.goodsbridge.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class AdminViewController {
    private final AdminService adminService;

    // 관리자 페이지 화면. 관리자 여부(isAdmin)만 넘기고, 대시보드와 접근 불가 안내 중 무엇을 보여줄지는 index.mustache가 정한다.
    @GetMapping("/test/admin")
    public String index(Authentication authentication, Model model) {
        model.addAttribute("isAdmin", isAdmin(authentication));
        return "admin/index";
    }

    // 대시보드 데이터 API. 권한은 SecurityConfig(/api/admin/** -> ROLE_ADMIN)가,
    // month 검증은 AdminService가 맡는다. 컨트롤러는 전달만 한다.
    @ResponseBody
    @GetMapping("/api/admin/dashboard")
    public AdminResponse adminDashboard(@RequestParam(required = false) String month) {
        return adminService.getDashboard(month);
    }

    // 로그인 상태이면서 ROLE_ADMIN 권한이 있는지 확인한다. (화면 분기용이라 예외는 던지지 않는다)
    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
