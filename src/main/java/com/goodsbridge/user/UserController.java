package com.goodsbridge.user;

import com.goodsbridge._core.security.AuthService;
import com.goodsbridge._core.security.AuthService.TokenResponse;
import com.goodsbridge._core.util.Define;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RequiredArgsConstructor
@Slf4j
@Controller
public class UserController {

    private final KakaoLoginService kakaoLoginService;
    private final AuthService authService;
    private final UserService userService;

    // 내 정보 화면의 가게 구독 결제용 (데모 모드에서는 결제창을 건너뛴다)
    @Value("${portone.store-id}")
    private String portoneStoreId;

    @Value("${portone.channel-key}")
    private String portoneChannelKey;

    @Value("${app.demo.enabled:false}")
    private boolean skipPayment;

    @GetMapping("/")
    public String index(@AuthenticationPrincipal Long userId, Model model) {
        model.addAttribute("userId", userId);
        model.addAttribute("isHome", true);
        return "user/index";
    }

    // 카카오 로그인 페이지 이동
    @GetMapping("/api/kakao-login")
    public String kakaoLogin() {
        return "redirect:" + kakaoLoginService.getAuthorizeUrl();
    }

    // 카카오 로그인 콜백
    @GetMapping("/oauth2/kakao")
    public String kakaoCallback(@RequestParam(name = "code", required = false) String code,
                                HttpServletResponse response) {
        // 1. 카카오 사용자 조회 및 우리 서비스 회원 조회/가입
        User user = kakaoLoginService.login(code);
        // 2. 우리 서비스 JWT 발급
        TokenResponse tokens = authService.login(user);
        // 3. Access / Refresh Token을 HttpOnly 쿠키로 저장
        authService.addAuthCookies(response, tokens);
        return "redirect:/";
    }

    // GET http://localhost:8080/update
    @GetMapping("/update")
    public String updateForm(@AuthenticationPrincipal Long userId, Authentication authentication, Model model) {
        model.addAttribute("userId", userId);
        // 관리자는 내 정보 화면이 필요 없어서 관리자 화면으로 보낸다
        if (authentication != null && authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return "redirect:/test/admin/shops";
        }
        model.addAttribute("user", userService.findUpdateForm(userId));
        model.addAttribute("portoneStoreId", portoneStoreId);
        model.addAttribute("portoneChannelKey", portoneChannelKey);
        model.addAttribute("skipPayment", skipPayment);
        return "user/update-form";
    }

    @PostMapping("/api/update")
    public String update(@AuthenticationPrincipal Long userId, UserRequest.UpdateDto updateDto){
        updateDto.validate();
        userService.updateById(userId, updateDto);
        return "redirect:/";
    }

    @PostMapping("/user/profile-image/delete")
    public String deleteProfileImage(@AuthenticationPrincipal Long userId) {
        userService.deleteProfileImage(userId);
        return "redirect:/user/detail";
    }

    // GET http://localhost:8080/login
    @GetMapping("/login")
    public String loginForm() {
        return "user/login-form";
    }

    @PostMapping("/api/login")
    public String login(@Valid UserRequest.LoginDto loginDto, HttpServletResponse response) {
        User user = userService.login(loginDto);
        TokenResponse tokens = authService.login(user);
        authService.addAuthCookies(response, tokens);
        return "redirect:/";
    }

    // GET http://localhost:8080/join
    @GetMapping("/join")
    public String joinForm() {
        return "user/join-form";
    }

    // POST http://localhost:8080/join
    @PostMapping("/api/join")
    public String join(@Valid UserRequest.JoinDto joinDto, HttpSession session) {
        String verifiedEmail = (String) session.getAttribute(Define.VERIFIED_EMAIL);
        userService.join(joinDto, verifiedEmail);

        session.removeAttribute(Define.VERIFIED_EMAIL);
        return "redirect:/login";
    }

    @PostMapping("/api/logout")
    public String logout(@AuthenticationPrincipal Long userId, HttpServletResponse response) {
        authService.logout(userId);          // DB에서 Refresh Token 해시 제거
        authService.clearAuthCookies(response); // accessToken, refreshToken 쿠키 만료
        return "redirect:/";
    }

    @PostMapping("/api/withdraw")
    public String withdraw(@AuthenticationPrincipal Long userId, HttpServletResponse response){
        userService.withdraw(userId, response);
        return "redirect:/";
    }

}
