package com.goodsbridge.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// 탈퇴하면 이메일·닉네임이 익명화되어, 같은 이메일/카카오 계정/닉네임으로 다시 가입할 수 있다
@SpringBootTest
@Transactional
class WithdrawReSignupTest {

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;

    @Test
    void 탈퇴하면_이메일_닉네임_프로필이_익명화된다() {
        User user = userRepository.save(User.builder()
            .email("anon@test.com").password("pw").nickname("익명테스트")
            .loginType(LoginType.EMAIL).profileImage("a.png").build());

        user.withdraw();

        assertThat(user.isActive()).isFalse();
        assertThat(user.getEmail()).isEqualTo("withdrawn-" + user.getId() + "@withdrawn.invalid");
        assertThat(user.getNickname()).isEqualTo("탈퇴회원" + user.getId());
        assertThat(user.getProfileImage()).isNull();
    }

    @Test
    void 탈퇴한_이메일과_닉네임으로_다시_가입할_수_있다() {
        User old = userRepository.save(User.builder()
            .email("again@test.com").password("pw").nickname("재가입").loginType(LoginType.EMAIL).build());
        old.withdraw();
        userRepository.flush();

        UserRequest.JoinDto joinDto = new UserRequest.JoinDto();
        joinDto.setEmail("again@test.com");
        joinDto.setPassword("newpass123!");
        joinDto.setNickname("재가입");

        userService.join(joinDto, "again@test.com");

        User created = userRepository.findByEmail("again@test.com").orElseThrow();
        assertThat(created.getId()).isNotEqualTo(old.getId());
        assertThat(created.isActive()).isTrue();
        assertThat(created.getNickname()).isEqualTo("재가입");
    }

    @Test
    void 탈퇴한_카카오_계정으로_다시_로그인하면_새_계정이_만들어진다() {
        UserResponse.KakaoUserInfo info = new UserResponse.KakaoUserInfo();
        info.setId(987654321L);
        info.setProperties(new UserResponse.KakaoUserInfo.Properties());

        User first = userService.kakaoLogin(info);
        first.withdraw();
        userRepository.flush();

        User second = userService.kakaoLogin(info);

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(second.isActive()).isTrue();
        assertThat(second.getEmail()).isEqualTo("987654321@kakao.com");
    }

    // 실제 탈퇴 서비스 → 이메일 중복 확인(인증 메일 단계) → 가입까지 이어서 확인한다
    @Test
    void 탈퇴_서비스로_탈퇴한_뒤_이메일_중복확인을_통과하고_재가입된다() {
        User old = userRepository.save(User.builder()
            .email("flow@test.com").password("pw").nickname("흐름").loginType(LoginType.EMAIL).build());

        userService.withdraw(old.getId(), org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletResponse.class));
        userRepository.flush();

        userService.checkEmailAvailable("flow@test.com");   // 예외가 나면 "이미 등록된 이메일입니다"

        UserRequest.JoinDto joinDto = new UserRequest.JoinDto();
        joinDto.setEmail("flow@test.com");
        joinDto.setPassword("newpass123!");
        joinDto.setNickname("흐름");
        userService.join(joinDto, "flow@test.com");

        assertThat(userRepository.findByEmail("flow@test.com").orElseThrow().getId()).isNotEqualTo(old.getId());
        assertThat(userRepository.findById(old.getId()).orElseThrow().isActive()).isFalse();
    }
}
