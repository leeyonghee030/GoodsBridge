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

    @Test
    void 가짜_결제는_모두_가짜_환불_처리되는_접두사를_쓴다() {
        demoDataService.reset();

        Integer bad = jdbcTemplate.queryForObject(
            "select count(*) from payment_tb where pg_tx_id = 'demo' and payment_id not like 'demo-pay-%'",
            Integer.class);

        // DemoPaymentService.refund 는 demo-pay- 로 시작하는 결제만 PortOne 없이 환불한다
        assertThat(bad).as("PortOne 으로 환불이 넘어가 실패하는 시연 결제").isZero();
    }
}
