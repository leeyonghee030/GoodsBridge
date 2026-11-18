package com.goodsbridge.demo;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    @Autowired EntityManager em;

    @Test
    void 탈퇴한_데모_계정은_로그인이_막히고_초기화하면_다시_로그인된다() {
        demoDataService.reset();
        User shopOwner = userRepository.findByEmail(DemoDataService.SHOP_EMAIL).orElseThrow();
        shopOwner.withdraw();
        em.flush();

        HttpServletResponse response = mock(HttpServletResponse.class);
        assertThatThrownBy(() -> demoController.login("shop", response))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("탈퇴한 데모 계정");

        demoDataService.reset();
        em.clear();

        assertThat(userRepository.findByEmail(DemoDataService.SHOP_EMAIL).orElseThrow().isActive()).isTrue();
        demoController.login("shop", response);
    }
}
