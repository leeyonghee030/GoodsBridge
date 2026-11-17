package com.goodsbridge.user;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.security.AuthService;
import com.goodsbridge.auction.AuctionRepository;
import com.goodsbridge.event.EventParticipationRepository;
import com.goodsbridge.exchange.ExchangeRequestRepository;
import com.goodsbridge.purchase.PurchaseRepository;
import com.goodsbridge.shop.ShopRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceWithdrawGuardTest {

    private static final Long USER_ID = 1L;

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock KakaoApiClient kakaoApiClient;
    @Mock AuthService authService;
    @Mock ShopRepository shopRepository;
    @Mock ExchangeRequestRepository exchangeRequestRepository;
    @Mock AuctionRepository auctionRepository;
    @Mock EventParticipationRepository eventParticipationRepository;
    @Mock PurchaseRepository purchaseRepository;

    UserService userService;
    User user;
    HttpServletResponse response = mock(HttpServletResponse.class);

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, kakaoApiClient, authService,
            shopRepository, exchangeRequestRepository, auctionRepository,
            eventParticipationRepository, purchaseRepository);
        user = User.builder().email("a@a.com").password("pw").nickname("닉").loginType(LoginType.KAKAO).build();
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void 운영중인_가게가_있으면_탈퇴를_거절한다() {
        when(shopRepository.existsByOwnerUserIdAndDeletedFalse(USER_ID)).thenReturn(true);
        assertRejected("운영 중인 가게");
    }

    @Test
    void 진행중인_교환이_있으면_탈퇴를_거절한다() {
        when(exchangeRequestRepository.existsOngoingByUserId(USER_ID)).thenReturn(true);
        assertRejected("진행 중인 교환");
    }

    @Test
    void 진행중인_경매가_있으면_탈퇴를_거절한다() {
        when(auctionRepository.existsOngoingByBidderId(USER_ID)).thenReturn(true);
        assertRejected("경매");
    }

    @Test
    void 참여중인_이벤트가_있으면_탈퇴를_거절한다() {
        when(eventParticipationRepository.existsOngoingByUserId(USER_ID)).thenReturn(true);
        assertRejected("참여 중인 이벤트");
    }

    @Test
    void 배송이_끝나지_않은_구매가_있으면_탈퇴를_거절한다() {
        when(purchaseRepository.existsUndeliveredByUserId(USER_ID)).thenReturn(true);
        assertRejected("배송이 끝나지 않은 구매");
    }

    @Test
    void 막는_조건이_없으면_탈퇴된다() {
        userService.withdraw(USER_ID, response);

        assertThat(user.isActive()).isFalse();
        verify(authService).logout(USER_ID);
    }

    @Test
    void 탈퇴한_계정의_이메일_로그인은_비밀번호가_틀린_것과_같은_문구로_거절한다() {
        user.withdraw();
        UserRequest.LoginDto loginDto = mock(UserRequest.LoginDto.class);
        when(loginDto.getEmail()).thenReturn("a@a.com");
        when(userRepository.findByEmail("a@a.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.login(loginDto))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("사용자명 또는 비밀번호가 올바르지 않습니다");
    }

    @Test
    void 탈퇴한_계정의_카카오_로그인은_WithdrawnUserException을_던진다() {
        user.withdraw();
        UserResponse.KakaoUserInfo info = new UserResponse.KakaoUserInfo();
        info.setId(123L);
        when(userRepository.findByEmail("123@kakao.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.kakaoLogin(info))
            .isInstanceOf(WithdrawnUserException.class);
    }

    private void assertRejected(String messagePart) {
        assertThatThrownBy(() -> userService.withdraw(USER_ID, response))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(messagePart);

        assertThat(user.isActive()).isTrue();
        verify(authService, never()).logout(USER_ID);
    }
}
