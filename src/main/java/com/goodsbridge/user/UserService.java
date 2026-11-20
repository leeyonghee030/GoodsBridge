package com.goodsbridge.user;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge._core.security.AuthService;
import com.goodsbridge._core.util.FileUtil;
import com.goodsbridge._core.util.NicknameGenerator;
import com.goodsbridge.auction.AuctionRepository;
import com.goodsbridge.event.EventParticipationRepository;
import com.goodsbridge.exchange.ExchangeRequestRepository;
import com.goodsbridge.purchase.PurchaseRepository;
import com.goodsbridge.shop.ShopRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
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
    private final ShopRepository shopRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;
    private final AuctionRepository auctionRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final PurchaseRepository purchaseRepository;

    // 구매·낙찰 잔금 결제 화면의 배송지에 미리 채울 "내 기본 주소" (내 정보에 저장한 주소).
    // 화면 조각 fragments/delivery-address 가 모델의 defaultAddress 로 쓴다. 주소가 없으면 null.
    public Map<String, String> findDefaultAddress(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
            .map(User::getAddress)
            .filter(address -> address != null && address.getAddress() != null && !address.getAddress().isBlank())
            .map(address -> Map.of(
                "postalCode", address.getPostalCode() == null ? "" : address.getPostalCode(),
                "address", address.getAddress(),
                "detailAddress", address.getDetailAddress() == null ? "" : address.getDetailAddress()))
            .orElse(null);
    }

    @Transactional
    public void join(UserRequest.JoinDto joinDto, String  verifiedEmail) {

        if (verifiedEmail == null || !verifiedEmail.equals(joinDto.getEmail())){
            throw new BadRequestException("이메일 인증을 먼저 완료해주세요");
        }

        userRepository.findByEmail(joinDto.getEmail()).ifPresent(user -> {
            log.warn("회원가입 실패 - 중복된 이메일 : {}", joinDto.getEmail());
            throw new BadRequestException("이미 존재하는 이메일입니다");
        });

        // 닉네임은 DB에서 겹칠 수 없다. 미리 막지 않으면 저장할 때 제약 위반으로 500 오류가 난다.
        rejectReservedNickname(joinDto.getNickname());
        if (userRepository.existsByNickname(joinDto.getNickname())) {
            throw new BadRequestException("이미 사용 중인 닉네임입니다");
        }

        String encodePassword = passwordEncoder.encode(joinDto.getPassword());
        User user = joinDto.toEntity(encodePassword, joinDto.getPhoneNumber());
        User savedUser = userRepository.save(user);

        log.info("회원가입 서비스 처리 완료 - 이메일: {}, ID: {} ", joinDto.getEmail(), savedUser.getId());
    }

    private User joinKakaoUser(String email, UserResponse.KakaoUserInfo kakaoUserInfo) {
        // 프로필 제공에 동의하지 않으면 properties 가 없다
        UserResponse.KakaoUserInfo.Properties properties = kakaoUserInfo.getProperties();
        User newUser = User.builder()
            // 카카오 사용자는 비밀번호로 로그인하지 않으므로 아무도 알 수 없는 값을 넣음
            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
            .email(email)
            .nickname(uniqueGeneratedNickname())
            .profileImage(properties == null ? null : properties.getProfileImage())
            .loginType(LoginType.KAKAO)
            .build();
        return userRepository.save(newUser);
    }

    // 기본 닉네임 생성기로 만든 닉네임이 이미 있으면 다시 만든다. 닉네임 컬럼은 unique 라 겹치면 저장할 때 500 오류가 난다.
    // 몇 번 다시 만들어도 겹치면(거의 없음) 마지막 닉네임 뒤에 번호를 붙인다.
    private String uniqueGeneratedNickname() {
        String nickname = NicknameGenerator.generate();
        for (int i = 0; i < NICKNAME_RETRY && userRepository.existsByNickname(nickname); i++) {
            nickname = NicknameGenerator.generate();
        }
        String base = nickname;
        for (int n = 1; userRepository.existsByNickname(nickname); n++) {
            nickname = base + "_" + n;
        }
        return nickname;
    }

    private static final int NICKNAME_RETRY = 5;

    // 탈퇴하면 닉네임을 "탈퇴회원"+id 로 바꾼다(User.withdraw). 다른 회원이 그 이름을 먼저 쓰면 그 회원이 탈퇴할 때 unique 위반이 나므로 막는다.
    private static void rejectReservedNickname(String nickname) {
        if (nickname != null && nickname.trim().startsWith(User.WITHDRAWN_NICKNAME_PREFIX)) {
            throw new BadRequestException("'" + User.WITHDRAWN_NICKNAME_PREFIX + "'(으)로 시작하는 닉네임은 사용할 수 없습니다");
        }
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
            throw new WithdrawnUserException();
        }

        // 3. 같은 이름의 일반 회원이 있다면 카카오 사용자로 로그인 시키지 않음
        if (user.getLoginType() != LoginType.KAKAO) {
            throw new BadRequestException("이미 사용 중인 사용자명입니다");
        }
        // 예전에 닉네임 없이 가입된 카카오 회원은 이번 로그인 때 닉네임을 채운다 (닉네임이 비면 게시판 등에서 작성자를 못 보여준다)
        if (user.getNickname() == null || user.getNickname().isBlank()) {
            user.updateNickname(uniqueGeneratedNickname());
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
            // 탈퇴한 계정이라는 사실이 드러나지 않도록 비밀번호가 틀린 경우와 같은 문구를 쓴다
            throw new BadRequestException("사용자명 또는 비밀번호가 올바르지 않습니다");
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

        // 지금 닉네임이 비어 있는 회원(예전 카카오 가입)도 바꿀 수 있게 null 쪽에서 비교하지 않는다
        if (updateDto.hasNickname()
            && !updateDto.getNickname().equals(user.getNickname())) {
            rejectReservedNickname(updateDto.getNickname());
            if (userRepository.existsByNicknameAndIdNot(updateDto.getNickname(), userId)) {
                throw new BadRequestException("이미 사용 중인 닉네임입니다");
            }
            user.updateNickname(updateDto.getNickname());
            log.info("Nickname : {}", user.getNickname());
        }

        if (updateDto.hasProfileImage()) {
            String newImage = FileUtil.saveImage(updateDto.getProfileImage());
            String oldImage = user.getProfileImage();
            user.updateProfileImage(newImage); // DB에는 새 파일 이름 (변경 감지)
            FileUtil.deleteFile(oldImage);
        }

        if (updateDto.hasAddress()){
            user.updateAddress(Address.builder()
                .postalCode(updateDto.getPostalCode())
                .address(updateDto.getAddress())
                .detailAddress(updateDto.getDetailAddress())
                .build());
        }

        if (updateDto.hasPhoneNumber()){
            user.updatePhoneNumber(updateDto.getPhonePrefix() + "-" +
                updateDto.getPhoneMiddle() + "-" +
                updateDto.getPhoneLast());
        }
    }

    @Transactional
    public void deleteProfileImage(Long userId) {
        User user = findById(userId);
        String oldImage = user.getProfileImage();
        user.updateProfileImage(null);
        FileUtil.deleteFile(oldImage);
    }

    // 돈이 걸려 있거나 상대방이 있는 활동이 남아 있으면 탈퇴를 막는다 (가게 삭제 차단과 같은 방식)
    private void validateCanWithdraw(Long userId) {
        if (shopRepository.existsByOwnerUserIdAndDeletedFalse(userId)) {
            throw new BadRequestException("운영 중인 가게가 있어 탈퇴할 수 없어요. 가게를 먼저 삭제해 주세요.");
        }
        if (exchangeRequestRepository.existsOngoingByUserId(userId)) {
            throw new BadRequestException("진행 중인 교환이 있어 탈퇴할 수 없어요. 교환을 마무리하거나 취소해 주세요.");
        }
        if (auctionRepository.existsOngoingByBidderId(userId)) {
            throw new BadRequestException("입찰 중이거나 잔금을 결제하지 않은 경매가 있어 탈퇴할 수 없어요. 경매를 마무리해 주세요.");
        }
        if (eventParticipationRepository.existsOngoingByUserId(userId)) {
            throw new BadRequestException("참여 중인 이벤트가 있어 탈퇴할 수 없어요. 이벤트가 끝난 뒤 다시 시도해 주세요.");
        }
        if (purchaseRepository.existsUndeliveredByUserId(userId)) {
            throw new BadRequestException("배송이 끝나지 않은 구매가 있어 탈퇴할 수 없어요. 배송 완료 후 다시 시도해 주세요.");
        }
    }

    // 트랜잭션이 롤백되면 파일만 지워지는 일이 없도록 커밋된 뒤에 지운다
    private void deleteFileAfterCommit(String filename) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    FileUtil.deleteFile(filename);
                }
            });
        } else {
            FileUtil.deleteFile(filename);
        }
    }

    @Transactional
    public void withdraw(Long userId, HttpServletResponse response) {
        User user = findById(userId);
        validateCanWithdraw(userId);
        String oldImage = user.getProfileImage();
        user.withdraw();
        deleteFileAfterCommit(oldImage);
        authService.logout(userId);
        authService.clearAuthCookies(response);

    }

    public class WithdrawnAccountException extends RuntimeException {

        public WithdrawnAccountException() {
            super("탈퇴한 계정입니다. 계정 복구 인증이 필요합니다.");
        }
    }
}
