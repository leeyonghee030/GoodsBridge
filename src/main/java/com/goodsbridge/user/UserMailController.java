package com.goodsbridge.user;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.util.Define;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UserMailController {

    private final MailService mailService;
    private final UserService userService;

    @PostMapping("/api/email/send")
    public ResponseEntity<?> sendCode(@RequestBody UserRequest.EmailCheckDto emailCheckDto, HttpSession session){

        emailCheckDto.validate();
        userService.checkEmailAvailable(emailCheckDto.getEmail());
        // 이미 가입된 이메일이 있으면 400 던져야 함

        // SMTP 를 사용해서 인증 번호 발송 (구글 메일 서버) -> 사용자 이메일 수신함 (인증 번호)
        mailService.sendVerificationCode(emailCheckDto.getEmail(), session);

        // 인증 5회 연속으로 틀릴 시 인증 초기화
        String attemptKey = "emailAttempt_" + emailCheckDto.getEmail();
        session.setAttribute(attemptKey, 0);

        return ResponseEntity.ok(Map.of("message", "인증 되었습니다"
            + Define.CODE_EXPIRE_MINUTES + "분 안에 입력하세요"));
    }

    // POST /api/email/verify
    // 요청 바디 {"email": "a@naver.com", "code" : "123456"
    @PostMapping("/api/email/verify")
    public ResponseEntity<?> verifyCode(@RequestBody UserRequest.EmailCheckDto emailCheckDto,
                                        HttpSession session){
        emailCheckDto.validateCode();
        boolean verified = mailService.verifyCode(emailCheckDto.getEmail(), emailCheckDto.getCode(), session);
        if (!verified){
            return ResponseEntity.badRequest().body(Map.of("message", "인증번호가 일치하지 않습니다"));
        }
        return ResponseEntity.ok().body(Map.of("message", "인증되었습니다"));
    }

    @ExceptionHandler(MailService.EmailCodeAttemptsExceededException.class)
    public ResponseEntity<Map<String, String>> handleAttemptsExceeded(
        MailService.EmailCodeAttemptsExceededException e) {
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(Map.of("message", e.getMessage()));
    }


    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, String>> handleEx400(BadRequestException e){
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleEx500(RuntimeException e){
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body((Map.of("message", "메일 발송 중 오류가 발생했습니다")));
    }
}
