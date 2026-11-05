package com.goodsbridge.shop;

import com.goodsbridge.user.Role;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShopServiceIntegrationTest {

    @Autowired
    private ShopService shopService;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("1. data.sql 초기 데이터를 기반으로 페이징된 승인된 가게 목록 조회 테스트")
    void findAllApprovedShopsTest() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ShopResponse> resultPage = shopService.findAll("굿즈샵", null, pageable);

        assertThat(resultPage.getContent()).hasSize(1);
        assertThat(resultPage.getContent().get(0).getName()).isEqualTo("홍대 굿즈샵");
        assertThat(resultPage.getContent().get(0).getStatus()).isEqualTo(ShopStatus.APPROVED);
    }

    @Test
    @DisplayName("2. 서비스 계층을 통한 신규 입점 신청 테스트")
    void createShopServiceTest() {
        ShopCreateRequest request = ShopCreateRequest.builder()
            .name("수원 굿즈샵")
            .region("경기")
            .address("경기도 수원시 팔달구")
            .build();

        ShopResponse response = shopService.create(1L, request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ShopStatus.PENDING);
    }

    @Test
    @DisplayName("3. 관리자 승인 로직 테스트")
    void approveShopServiceTest() {
        Shop pendingShop = shopRepository.findAll().stream()
            .filter(s -> s.getStatus() == ShopStatus.PENDING)
            .findFirst()
            .orElseThrow();

        Long adminUserId = userRepository.findAll().stream()
            .filter(u -> u.getRole() == Role.ADMIN)
            .findFirst()
            .orElseThrow()
            .getId();

        shopService.approve(adminUserId, pendingShop.getId());

        Shop updatedShop = shopRepository.findById(pendingShop.getId()).orElseThrow();
        assertThat(updatedShop.getStatus()).isEqualTo(ShopStatus.APPROVED);
    }

    @Test
    @DisplayName("4. 관리자가 아닌 유저가 승인을 시도하면 ForbiddenException이 발생한다")
    void approveShopServiceTest_forbidden() {
        Shop pendingShop = shopRepository.findAll().stream()
            .filter(s -> s.getStatus() == ShopStatus.PENDING)
            .findFirst()
            .orElseThrow();

        Long nonAdminUserId = pendingShop.getOwnerUserId();

        org.junit.jupiter.api.Assertions.assertThrows(
            com.goodsbridge._core.exception.ForbiddenException.class,
            () -> shopService.approve(nonAdminUserId, pendingShop.getId())
        );
    }

    @Test
    @DisplayName("5. 가게 삭제(소프트 딜리트) 시 목록 조회 및 상세 조회에서 배제된다")
    void deleteShopSoftDeleteTest() {
        // given: 승인된 가게 하나 조회
        Shop approvedShop = shopRepository.findAll().stream()
            .filter(s -> s.getStatus() == ShopStatus.APPROVED && !s.isDeleted())
            .findFirst()
            .orElseThrow();

        Long ownerId = approvedShop.getOwnerUserId();
        Long shopId = approvedShop.getId();

        // when: 소유주가게 삭제(소프트 딜리트) 요청
        shopService.delete(ownerId, shopId);

        // then 1: 삭제된 가게는 APPROVED 목록 검색에 나타나지 않음
        Pageable pageable = PageRequest.of(0, 10);
        Page<ShopResponse> results = shopService.findAll(null, null, pageable);
        boolean containsDeleted = results.getContent().stream()
            .anyMatch(s -> s.getId().equals(shopId));
        assertThat(containsDeleted).isFalse();

        // then 2: 상세 조회 시 NotFoundException이 발생해야 함
        org.junit.jupiter.api.Assertions.assertThrows(
            com.goodsbridge._core.exception.NotFoundException.class,
            () -> shopService.findById(shopId)
        );
    }

    @Test
    @DisplayName("6. 사장님 본인 가게 페이징 목록 조회 테스트")
    void findMyShopsPagingTest() {
        // given: 특정 유저(예: ID 1번)가 가진 가게 생성
        ShopCreateRequest request = ShopCreateRequest.builder()
            .name("내 굿즈샵")
            .region("서울")
            .address("서울특별시 강남구")
            .build();
        shopService.create(1L, request);

        // when: 본인 가게 목록 페이징 조회
        Pageable pageable = PageRequest.of(0, 10);
        Page<ShopResponse> myShops = shopService.findAllByOwnerUserId(1L, pageable);

        // then: 조회된 목록 중에 방금 생성한 '내 굿즈샵'이 포함되어 있는지 확인
        assertThat(myShops.getContent()).isNotEmpty();
        boolean exists = myShops.getContent().stream()
            .anyMatch(shop -> shop.getName().equals("내 굿즈샵"));
        assertThat(exists).isTrue();
    }
}
