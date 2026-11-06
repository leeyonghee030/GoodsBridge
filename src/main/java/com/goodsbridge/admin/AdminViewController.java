package com.goodsbridge.admin;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Controller
@RequiredArgsConstructor
public class AdminViewController {

    private final AdminService adminService;

    @GetMapping("/test/admin")
    public String index(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return "admin/forbidden";
        }
        return "admin/index";
    }

    @ResponseBody
    @GetMapping("/api/admin/dashboard")
    public AdminResponse adminDashboard(Authentication authentication,
                                        @RequestParam(required = false) String month) {
        checkAdmin(authentication);
        YearMonth selectedMonth = parseMonth(month);
        return adminService.getDashboard(selectedMonth);
    }

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

    private void checkAdmin(Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new ForbiddenException("관리자만 접근할 수 있습니다.");
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority ->
                authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
