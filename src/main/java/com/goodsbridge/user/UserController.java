package com.goodsbridge.user;

import com.goodsbridge._core.security.AuthService;
import com.goodsbridge._core.security.AuthService.TokenResponse;
import com.goodsbridge._core.util.Define;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @GetMapping("/")
    public String index(@AuthenticationPrincipal Long userId, Model model) {
        model.addAttribute("userId", userId);
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
    public String updateForm(@AuthenticationPrincipal Long userId, Model model) {
        model.addAttribute("user", userService.findUpdateForm(userId));
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
