package com.goodsbridge.user;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.util.NicknameGenerator;
import com.goodsbridge.board.Board;
import com.goodsbridge.board.BoardRepository;
import com.goodsbridge.board.BoardResponseDto;
import com.goodsbridge.board.BoardService;
import com.goodsbridge.board.BoardType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

// 닉네임이 비거나 겹칠 때: 카카오 가입·로그인은 기본 닉네임 생성기로 겹치지 않는 닉네임을 채우고, 일반 가입·정보 수정은 중복을 400 으로 막고,
// 게시판 목록은 닉네임이 빈 작성자가 있어도 깨지지 않는다.
@SpringBootTest
@ActiveProfiles("test")
class NicknameIntegrityTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private BoardService boardService;
    @Autowired private BoardRepository boardRepository;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> boardIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        boardRepository.deleteAllById(boardIds);
        userRepository.deleteAllById(userIds);
    }

    @Test
    void 카카오로_처음_가입하면_기본_닉네임_생성기로_닉네임을_채운다() {
        User user = track(userService.kakaoLogin(kakao(randomKakaoId(), "카카오이름")));

        assertThat(user.getNickname()).isNotBlank();
    }

    @Test
    void 생성한_닉네임이_이미_쓰이고_있으면_다시_만들어_겹치지_않게_한다() {
        String taken = "겹침" + shortId();
        String fresh = "새이름" + shortId();
        track(userRepository.save(new User("a-" + shortId() + "@test.com", "pw", taken, LoginType.EMAIL, null)));

        try (MockedStatic<NicknameGenerator> generator = mockStatic(NicknameGenerator.class)) {
            generator.when(NicknameGenerator::generate).thenReturn(taken, fresh);

            User user = track(userService.kakaoLogin(kakao(randomKakaoId(), null)));

            assertThat(user.getNickname()).isEqualTo(fresh);
        }
    }

    @Test
    void 다시_만들어도_계속_겹치면_뒤에_번호를_붙인다() {
        String taken = "계속겹침" + shortId();
        track(userRepository.save(new User("h-" + shortId() + "@test.com", "pw", taken, LoginType.EMAIL, null)));

        try (MockedStatic<NicknameGenerator> generator = mockStatic(NicknameGenerator.class)) {
            generator.when(NicknameGenerator::generate).thenReturn(taken);

            User user = track(userService.kakaoLogin(kakao(randomKakaoId(), null)));

            assertThat(user.getNickname()).isEqualTo(taken + "_1");
        }
    }

    @Test
    void 카카오_프로필_제공에_동의하지_않아도_가입된다() {
        UserResponse.KakaoUserInfo info = kakao(randomKakaoId(), null);
        info.setProperties(null);

        User user = track(userService.kakaoLogin(info));

        assertThat(user.getNickname()).isNotBlank();
        assertThat(user.getProfileImage()).isNull();
    }

    @Test
    void 닉네임이_비어_있는_기존_카카오_회원은_다음_로그인_때_닉네임을_채운다() {
        long kakaoId = randomKakaoId();
        track(userRepository.save(new User(kakaoId + "@kakao.com", "pw", null, LoginType.KAKAO, null)));

        User user = userService.kakaoLogin(kakao(kakaoId, "카카오이름"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getNickname()).isNotBlank();
    }

    @Test
    void 일반_가입에서_이미_쓰이는_닉네임이면_400으로_막는다() {
        String nickname = "가입중복" + shortId();
        track(userRepository.save(new User("b-" + shortId() + "@test.com", "pw", nickname, LoginType.EMAIL, null)));
        UserRequest.JoinDto join = new UserRequest.JoinDto();
        String email = "c-" + shortId() + "@test.com";
        join.setEmail(email);
        join.setPassword("password123");
        join.setNickname(nickname);

        assertThatThrownBy(() -> userService.join(join, email))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("닉네임");
    }

    @Test
    void 내_정보_수정에서_다른_사람_닉네임으로_바꾸면_400으로_막는다() {
        String taken = "수정중복" + shortId();
        track(userRepository.save(new User("d-" + shortId() + "@test.com", "pw", taken, LoginType.EMAIL, null)));
        User me = track(userRepository.save(new User("e-" + shortId() + "@test.com", "pw", "나" + shortId(), LoginType.EMAIL, null)));
        UserRequest.UpdateDto update = new UserRequest.UpdateDto();
        update.setNickname(taken);

        assertThatThrownBy(() -> userService.updateById(me.getId(), update))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("닉네임");
    }

    @Test
    void 닉네임이_비어_있는_회원도_내_정보에서_닉네임을_정할_수_있다() {
        User me = track(userRepository.save(new User("f-" + shortId() + "@kakao.com", "pw", null, LoginType.KAKAO, null)));
        String nickname = "새이름" + shortId();
        UserRequest.UpdateDto update = new UserRequest.UpdateDto();
        update.setNickname(nickname);

        userService.updateById(me.getId(), update);

        assertThat(userRepository.findById(me.getId()).orElseThrow().getNickname()).isEqualTo(nickname);
    }

    @Test
    void 게시판_목록은_닉네임이_빈_작성자가_있어도_깨지지_않고_알_수_없음으로_보여준다() {
        User author = track(userRepository.save(new User("g-" + shortId() + "@kakao.com", "pw", null, LoginType.KAKAO, null)));
        Board board = boardRepository.save(Board.builder()
            .title("닉네임 없는 글 " + shortId()).content("내용").authorId(author.getId()).boardType(BoardType.COMMUNITY).build());
        boardIds.add(board.getId());

        List<BoardResponseDto> boards = boardService.getBoards(BoardType.COMMUNITY, null, PageRequest.of(0, 100), null).getContent();

        assertThat(boards).filteredOn(b -> b.getId().equals(board.getId()))
            .extracting(BoardResponseDto::getAuthorNickname).containsExactly("알 수 없음");
    }

    private User track(User user) {
        userIds.add(user.getId());
        return user;
    }

    private static UserResponse.KakaoUserInfo kakao(long id, String nickname) {
        UserResponse.KakaoUserInfo info = new UserResponse.KakaoUserInfo();
        info.setId(id);
        UserResponse.KakaoUserInfo.Properties properties = new UserResponse.KakaoUserInfo.Properties();
        properties.setNickname(nickname);
        info.setProperties(properties);
        return info;
    }

    private static long randomKakaoId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
