package com.goodsbridge.demo;

import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge._core.security.AuthService;
import com.goodsbridge._core.util.SecurityUtil;
import com.goodsbridge.user.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// 시연용: 로그인 없이 일반유저/가게/관리자 계정으로 즉시 전환. app.demo.enabled=true 일 때만 존재한다.
@RestController
@RequestMapping("/demo")
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoController {

    private static final Map<String, String> EMAILS = Map.of(
        "user-a", DemoDataService.USER_A_EMAIL,
        "user-b", DemoDataService.USER_B_EMAIL,
        "shop", DemoDataService.SHOP_EMAIL,
        "shop-b", DemoDataService.SHOP_B_EMAIL,
        "admin", DemoDataService.ADMIN_EMAIL
    );

    private final UserRepository userRepository;
    private final AuthService authService;
    private final DemoDataService demoDataService;

    // 헤더가 데모 모드인지 확인하는 용도
    @GetMapping("/status")
    public Map<String, Object> status() {
        // 지금 로그인한 계정이 데모 계정이면 그 종류(user-a/user-b/shop/admin)를 같이 내려준다
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        String current = userId == null ? "" : userRepository.findById(userId)
            .flatMap(u -> EMAILS.entrySet().stream()
                .filter(e -> e.getValue().equals(u.getEmail()))
                .map(Map.Entry::getKey)
                .findFirst())
            .orElse("");
        return Map.of("enabled", true, "current", current);
    }

    @PostMapping("/login/{type}")
    public void login(@PathVariable String type, HttpServletResponse response) {
        String email = EMAILS.get(type);
        if (email == null) {
            throw new NotFoundException("알 수 없는 데모 계정입니다.");
        }
        var user = userRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException("데모 계정이 아직 생성되지 않았습니다."));
        authService.addAuthCookies(response, authService.login(user));
    }

    // 시연 데이터를 처음 상태로 되돌린다 (교환 요청/댓글/구매 등 시연 중 바뀐 값 초기화)
    @PostMapping("/reset")
    public void reset() {
        demoDataService.reset();
    }
}
