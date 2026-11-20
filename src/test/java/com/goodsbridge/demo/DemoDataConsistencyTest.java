package com.goodsbridge.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// 시연 데이터가 실제 서비스 규칙과 어긋난 상태(화면에서 불가능한 조합)로 만들어지지 않는지 확인한다
@SpringBootTest(properties = "app.demo.enabled=true")
@Transactional
class DemoDataConsistencyTest {

    @Autowired DemoDataService demoDataService;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void 환불_요청중인_구매는_배송_준비중이거나_배송_완료_상태다() {
        demoDataService.reset();

        Integer bad = jdbcTemplate.queryForObject(
            "select count(*) from refund_tb r "
                + "join payment_tb p on r.payment_id = p.id "
                + "join purchase_tb pu on pu.payment_id = p.payment_id "
                + "where r.status = 'REQUESTED' and pu.delivery_status not in ('PREPARING', 'DELIVERED')",
            Integer.class);

        assertThat(bad).as("배송 중인데 환불 요청이 걸린 구매").isZero();
    }

    @Test
    void 전액_환불된_구매_결제는_구매도_환불_상태다() {
        demoDataService.reset();

        Integer bad = jdbcTemplate.queryForObject(
            "select count(*) from purchase_tb pu "
                + "join payment_tb p on pu.payment_id = p.payment_id "
                + "where p.status = 'CANCELLED' and pu.status <> 'REFUNDED'",
            Integer.class);

        assertThat(bad).as("결제는 전액 환불됐는데 구매는 환불 상태가 아닌 건").isZero();
    }
}
