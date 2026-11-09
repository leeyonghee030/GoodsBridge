package com.goodsbridge.user;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge._core.security.AuthService;
import com.goodsbridge._core.util.FileUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KakaoApiClient kakaoApiClient;
    private final AuthService authService;

    @Transactional
    public UserResponse.JoinDto join(UserRequest.JoinDto joinDto, String  verifiedEmail) {

        if (verifiedEmail == null || !verifiedEmail.equals(joinDto.getEmail())){
            throw new BadRequestException("이메일 인증을 먼저 완료해주세요");
        }

        userRepository.findByEmail(joinDto.getEmail()).ifPresent(user -> {
            log.warn("회원가입 실패 - 중복된 이메일 : {}", joinDto.getEmail());
            throw new BadRequestException("이미 존재하는 이메일입니다");
        });

        String encodePassword = passwordEncoder.encode(joinDto.getPassword());
        User user = joinDto.toEntity(encodePassword);
        User savedUser = userRepository.save(user);

        log.info("회원가입 서비스 처리 완료 - 이메일: {}, ID: {} ", joinDto.getEmail(), savedUser.getId());
        return new UserResponse.JoinDto(savedUser);
    }

    private User joinKakaoUser(String email, UserResponse.KakaoUserInfo kakaoUserInfo) {
        User newUser = User.builder()
            // 카카오 사용자는 비밀번호로 로그인하지 않으므로 아무도 알 수 없는 값을 넣음
            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
            .email(email)
            .profileImage(kakaoUserInfo.getProperties().getProfileImage())
            .loginType(LoginType.KAKAO)
            .build();
        return userRepository.save(newUser);
    }

    private User findById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));
    }

    public void checkEmailAvailable(String email) {
        userRepository.findByEmail(email)
            .ifPresent(user -> {
                throw new BadRequestException("이미 등록된 이메일입니다");
            });
    }

    @Transactional
    public User kakaoLogin(UserResponse.KakaoUserInfo kakaoUserInfo) {
        // 1. 카카오 회원번호로 우리 서비스의 사용자명 만들기
        String userId = kakaoUserInfo.getId() + "@kakao.com";

        // 2. 이미 가입 했는지 확인, 없으면 자동 회원가입
        User user = userRepository.findByEmail(userId)
            .orElseGet(() -> joinKakaoUser(userId, kakaoUserInfo));

        if (!user.isActive()) {
            throw new BadRequestException("해당 계정은 정지된 계정입니다");
        }

        // 3. 같은 이름의 일반 회원이 있다면 카카오 사용자로 로그인 시키지 않음
        if (user.getLoginType() != LoginType.KAKAO) {
            throw new BadRequestException("이미 사용 중인 사용자명입니다");
        }
        // 트랜잭션 안에서 SessionUserDto 생성
        return user;
    }

    public User login(UserRequest.LoginDto loginDto) {
        log.info("로그인 서비스 처리 시작 - 사용자 이메일: {}", loginDto.getEmail());

        // 1. 사용자명으로만 조회 (역할 포함)
        User user = userRepository.findByEmail(
                loginDto.getEmail())
            .orElseThrow(() -> {
                log.warn("로그인 실패 - 사용자명 : {}, 원인 : 인증정보 불일치", loginDto.getEmail());
                return new BadRequestException("사용자명 또는 비밀번호가 올바르지 않습니다");
            });

        if (!user.isActive()) {
            throw new BadRequestException("해당 계정은 정지된 계정입니다");
        }

        // 2. 비밀번호 비교 : matchs(입력한 평문, DB에 저장된 해쉬)
        if (!passwordEncoder.matches(loginDto.getPassword(), user.getPassword())) {
            log.warn("로그인 실패 - 사용자명 : {}, 원인 : 인증정보 불일치", loginDto.getEmail());
            throw new BadRequestException("사용자명 또는 비밀번호가 올바르지 않습니다");
        }

        log.info("로그인 성공 - 사용자 이메일: {}", loginDto.getEmail());
        return user;
    }

    public UserResponse.UpdateFormDto findUpdateForm(Long userId) {
        return new UserResponse.UpdateFormDto(findById(userId));
    }

    @Transactional
    public void updateById(Long userId, UserRequest.UpdateDto updateDto) {
        User user = findById(userId);

        if (updateDto.hasPassword()) {
            user.updatePassword(passwordEncoder.encode(updateDto.getPassword()));
        }

        if (updateDto.hasNickname()) {
            user.updateNickname(updateDto.getNickname());
        }

        if (updateDto.hasProfileImage()) {
            String newImage = FileUtil.saveImage(updateDto.getProfileImage());
            String oldImage = user.getProfileImage();
            user.updateProfileImage(newImage); // DB에는 새 파일 이름 (변경 감지)
            FileUtil.deleteFile(oldImage);
        }
    }

    @Transactional
    public void deleteProfileImage(Long userId) {
        User user = findById(userId);
        String oldImage = user.getProfileImage();
        user.updateProfileImage(null);
        FileUtil.deleteFile(oldImage);
    }

    @Transactional
    public void withdraw(Long userId, HttpServletResponse response) {
        User user = findById(userId);
        user.withdraw();
        authService.logout(userId);
        authService.clearAuthCookies(response);

    }
}
