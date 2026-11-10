package com.goodsbridge.admin;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Controller
@RequiredArgsConstructor
public class AdminViewController {
    private final AdminService adminService;

    // 관리자 페이지 화면. 관리자가 아니면 예외 대신 접근 불가 화면을 보여준다.
    @GetMapping("/test/admin")
    public String index(Authentication authentication, Model model) {
        model.addAttribute("userId", currentUserId(authentication));
        if (!isAdmin(authentication)) {
            return "admin/forbidden";
        }
        return "admin/index";
    }

    // 대시보드 데이터 API. month(YYYY-MM)를 받아 해당 월 통계를 JSON으로 반환
    // month를 생략하면 이번 달 기준임.
    @ResponseBody
    @GetMapping("/api/admin/dashboard")
    public AdminResponse adminDashboard(Authentication authentication,
                                        @RequestParam(required = false) String month) {
        checkAdmin(authentication);
        YearMonth selectedMonth = parseMonth(month);
        return adminService.getDashboard(selectedMonth);
    }

    // month 문자열을 YearMonth로 변환한다. 비어 있으면 이번 달, 형식이 틀리면 400 에러.
    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("month는 YYYY-MM 형식이어야 합니다.");
        }
    }

    // API용 권한 검사. 관리자가 아니면 ForbiddenException을 던진다.
    private void checkAdmin(Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new ForbiddenException("관리자만 접근할 수 있습니다.");
        }
    }

    // 로그인 상태이면서 ROLE_ADMIN 권한이 있는지 확인한다.
    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority ->
                authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return (Long) authentication.getPrincipal();
    }
}
