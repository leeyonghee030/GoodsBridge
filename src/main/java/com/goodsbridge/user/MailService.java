package com.goodsbridge.user;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.InternalServerException;
import com.goodsbridge._core.util.Define;
import com.goodsbridge._core.util.MailUtil;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.Serializable;
import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
@Service
public class MailService {

    //의존성 설정, application.yaml의 spring.mail 설정으로 스프링 부트가 만들어준 빈 객체이다.
    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    // record 문법 : JDK 16 정식으로 사용할 수 있음
    // 이 아래 한 줄이 클래스를 설계해준다. 필드 + getter + equals + hashCode + toString 을 가진 녀석을
    // 자바가 대신 만들어 준다. (대신 setter 가 없다)
    private record EmailCode(String code, LocalDateTime expireAt) implements Serializable {
        boolean isExpired() {
            return LocalDateTime.now().isAfter(expireAt);
        }
    }

    // 1. 인증번호 발송
    public void sendVerificationCode(String email, HttpSession session) {
        // 1-1. 6자자리 인증번호 생성
        String code = MailUtil.generateCode();
        // 1-2 메일 작성 (MimeMessage : HTML 본문, 첨부 파일 등을 담을 수 있는 메일 형식)
        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            // false : 첨부 파일 없음, "UTF-8" : 한글 깨짐 방지
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress); // 보내는 사람 이메일 주소 (미설정 시 OS 계정명으로 대체되어 발송 실패함)
            helper.setTo(email); // 받는 사람 이메일 주소
            helper.setSubject("[Goods Bridge] 회원가입 인증번호"); // 제목
            helper.setText("<h3>인증번호는 " + code + " 입니다</h3>" +
                "<p>" + Define.CODE_EXPIRE_MINUTES + " 분 안에 입력해주세요 </p>", true); // 본문

            // 1-3 발송 (실패하면 ... )
            javaMailSender.send(message);

        } catch (Exception e) {
            log.error("메일 발송 실패", e);
            throw new InternalServerException("메일 작성 중 오류가 발생했습니다");
        }

        session.setAttribute(Define.EMAIL_CODE_PREFIX + email,
            new EmailCode(code, LocalDateTime.now().plusMinutes(Define.CODE_EXPIRE_MINUTES))); // 분
        log.info("인증번호 발송 완료 - 이메일 :  {}", email); // 인증 번호 자체는 로그에 안남기는 게 좋음
    }

    // 2. 인증번호 확인 : 일치하면 true
    public boolean verifyCode(String email, String code, HttpSession session) {
        String key = Define.EMAIL_CODE_PREFIX + email;
        EmailCode savedEmailCode = (EmailCode) session.getAttribute(key);

        // emailCode_a@naver.com : 123456
        if (savedEmailCode == null) {
            throw new BadRequestException("인증번호를 먼저 요청해주세요");
        }
        if (savedEmailCode.isExpired()) {
            session.removeAttribute(key); // 만료된 인증 번호는 세션에서 삭제
            throw new BadRequestException("인증번호가 만료되었습니다. 다시 요청해주세요");
        }
        if (!savedEmailCode.code().equals(code)) {

            String attemptKey = "emailAttempt_" + email;
            String codeKey = Define.EMAIL_CODE_PREFIX + email;

            Integer savedAttempts = (Integer) session.getAttribute(attemptKey);
            int attempts = (savedAttempts == null ? 0 : savedAttempts) + 1;

            if (attempts >= 5) {
                session.removeAttribute(codeKey);
                session.removeAttribute(attemptKey);
                throw new EmailCodeAttemptsExceededException(
                    "인증 시도 횟수를 초과했습니다. 인증번호를 다시 요청해주세요."
                );
            }

            session.setAttribute(attemptKey, attempts);
            throw new BadRequestException("인증번호가 일치하지 않습니다");
        }
        // 성공 :  사용한 인증번호는 삭제 (한 번만 쓸 수 있게)
        session.removeAttribute(key);
        // 인증을 마친 이메일을 세션에 기록해야 함 (회원가입 시 서버가 확인) - 인증된 이메일이 아닌 변조해서 던질 수 있음
        session.setAttribute(Define.VERIFIED_EMAIL, email);
        return true;
    }

    public static class EmailCodeAttemptsExceededException
        extends RuntimeException {
        public EmailCodeAttemptsExceededException(String message) {
            super(message);
        }
    }
}
