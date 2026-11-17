package com.goodsbridge.demo;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@SpringBootTest(properties = "app.demo.enabled=true")
@Transactional
class DemoWithdrawnLoginTest {

    @Autowired DemoController demoController;
    @Autowired DemoDataService demoDataService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager em;

    HttpServletResponse response = mock(HttpServletResponse.class);

    @Test
    void 탈퇴하면_이메일이_익명화되어_데모_로그인이_안되고_초기화하면_새_계정으로_다시_로그인된다() {
        demoDataService.reset();
        User oldOwner = userRepository.findByEmail(DemoDataService.SHOP_EMAIL).orElseThrow();
        Long oldId = oldOwner.getId();
        oldOwner.withdraw();
        em.flush();

        assertThatThrownBy(() -> demoController.login("shop", response))
            .isInstanceOf(NotFoundException.class);

        demoDataService.reset();
        em.clear();

        User newOwner = userRepository.findByEmail(DemoDataService.SHOP_EMAIL).orElseThrow();
        assertThat(newOwner.getId()).isNotEqualTo(oldId);
        assertThat(newOwner.isActive()).isTrue();
        demoController.login("shop", response);
    }

    // 이메일 익명화 이전에 탈퇴해서 이메일이 그대로 남아 있는 계정(status만 WITHDRAWN)도 막고, 초기화로 복구한다
    @Test
    void 이메일이_남은_탈퇴_계정은_로그인이_막히고_초기화하면_복구된다() {
        demoDataService.reset();
        jdbcTemplate.update("update user_tb set status = 'WITHDRAWN' where email = ?", DemoDataService.SHOP_EMAIL);
        em.clear();

        assertThatThrownBy(() -> demoController.login("shop", response))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("탈퇴한 데모 계정");

        demoDataService.reset();
        em.clear();

        assertThat(userRepository.findByEmail(DemoDataService.SHOP_EMAIL).orElseThrow().isActive()).isTrue();
        demoController.login("shop", response);
    }
}
