package com.goodsbridge.shop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Sql(value = "/data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ShopServiceIntegrationTest {

    @Autowired
    private ShopService shopService;

    @Autowired
    private ShopJpaRepository shopRepository;

    @Test
    @DisplayName("1. data.sql 초기 데이터를 기반으로 승인된 가게 목록 조회 테스트")
    void findAllApprovedShopsTest() {
        // when: 승인된 가게('굿즈샵') 검색
        List<ShopResponse> results = shopService.findAll("굿즈샵", null);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("홍대 굿즈샵");
        assertThat(results.get(0).getStatus()).isEqualTo(ShopStatus.APPROVED);
    }

    @Test
    @DisplayName("2. 서비스 계층을 통한 신규 입점 신청 테스트")
    void createShopServiceTest() {
        // given
        ShopCreateRequest request = ShopCreateRequest.builder()
            .name("수원 굿즈샵")
            .region("경기")
            .address("경기도 수원시 팔달구")
            .build();

        ShopResponse response = shopService.create(1L, request);

        // then
        assertThat(response.getId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ShopStatus.PENDING);
    }

    @Test
    @DisplayName("3. 관리자 승인 로직 테스트")
    void approveShopServiceTest() {
        // given: data.sql에 있는 PENDING 상태의 가게 찾기
        Shop pendingShop = shopRepository.findAll().stream()
            .filter(s -> s.getStatus() == ShopStatus.PENDING)
            .findFirst()
            .orElseThrow();

        // when: 관리자 승인 메서드 호출
        shopService.approve(pendingShop.getId());

        // then: 상태가 APPROVED로 변경되었는지 확인
        Shop updatedShop = shopRepository.findById(pendingShop.getId()).orElseThrow();
        assertThat(updatedShop.getStatus()).isEqualTo(ShopStatus.APPROVED);
    }
}
