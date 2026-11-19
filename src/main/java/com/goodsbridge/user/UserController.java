package com.goodsbridge.user;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.security.AuthService;
import com.goodsbridge._core.security.AuthService.TokenResponse;
import com.goodsbridge._core.util.Define;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


@RequiredArgsConstructor
@Slf4j
@Controller
public class UserController {

    static final String KAKAO_REAUTH_COOKIE = "kakaoReauth";

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
    public String kakaoLogin(@RequestParam(name = "reselect", defaultValue = "false") boolean reselect,
                             @CookieValue(name = KAKAO_REAUTH_COOKIE, required = false) String reauth,
                             HttpServletResponse response) {
        String prompt = null;
        if (reselect) {
            prompt = KakaoApiClient.PROMPT_SELECT_ACCOUNT;
        }
        if (reauth != null) {
            // 방금 탈퇴한 브라우저의 첫 카카오 로그인: 카카오에 로그인돼 있는 같은 계정으로 바로 재가입되지 않도록 다시 로그인하게 한다.
            // 한 번만 쓰도록 표시 쿠키는 바로 지운다.
            prompt = KakaoApiClient.PROMPT_LOGIN;
            response.addHeader("Set-Cookie", kakaoReauthCookie("", Duration.ZERO).toString());
        }
        return "redirect:" + kakaoLoginService.getAuthorizeUrl(prompt);
    }

    private static ResponseCookie kakaoReauthCookie(String value, Duration maxAge) {
        return ResponseCookie.from(KAKAO_REAUTH_COOKIE, value)
            .httpOnly(true)
            .path("/")
            .sameSite("Lax")
            .maxAge(maxAge)
            .build();
    }

    // 카카오 로그인 콜백
    @GetMapping("/oauth2/kakao")
    public String kakaoCallback(@RequestParam(name = "code", required = false) String code,
                                @RequestParam(name = "state", required = false) String state,
                                HttpServletResponse response,
                                RedirectAttributes redirectAttributes) {
        // 1. 카카오 사용자 조회 및 우리 서비스 회원 조회/가입
        User user;
        try {
            user = kakaoLoginService.login(code);
        } catch (WithdrawnUserException e) {
            // 탈퇴한 계정: 안내 후 카카오 계정 선택 화면으로 다시 보내 다른 계정으로 로그인하게 한다.
            // 계정 선택을 이미 거친 뒤에도 탈퇴한 계정이면 또 보내지 않고 로그인 팝업에서 안내만 한다.
            redirectAttributes.addFlashAttribute("loginError", e.getMessage());
            if (!KakaoApiClient.RESELECT_STATE.equals(state)) {
                redirectAttributes.addFlashAttribute("kakaoRetry", true);
            }
            return "redirect:/";
        } catch (BadRequestException e) {
            // 그 밖에 로그인할 수 없는 경우: 에러 페이지 대신 홈으로 돌려보내 로그인 팝업에서 안내한다
            redirectAttributes.addFlashAttribute("loginError", e.getMessage());
            return "redirect:/";
        }
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
    public String loginForm(@AuthenticationPrincipal Long userId) {
        // 로그인은 헤더의 로그인 팝업으로 통일한다. 홈으로 보내면서 팝업을 열어 달라고 표시한다
        if (userId != null) {
            return "redirect:/";
        }
        return "redirect:/?login=open";
    }

    @PostMapping("/api/login")
    public ResponseEntity<Void> login(@Valid UserRequest.LoginDto loginDto, HttpServletResponse response) {
        // ResponseEntity 를 돌려주므로 실패하면 에러 페이지 대신 JSON 에러가 내려간다.
        // 로그인 화면들(헤더 로그인 창, /login 페이지)이 fetch 로 받아 얼럿으로 안내하고, 성공하면 직접 이동한다.
        User user = userService.login(loginDto);
        TokenResponse tokens = authService.login(user);
        authService.addAuthCookies(response, tokens);
        return ResponseEntity.ok().build();
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
        // 다음 카카오 로그인 때 카카오 로그인을 다시 하게 하려는 표시 (하루 동안 유지)
        response.addHeader("Set-Cookie", kakaoReauthCookie("1", Duration.ofDays(1)).toString());
        return "redirect:/";
    }

}
