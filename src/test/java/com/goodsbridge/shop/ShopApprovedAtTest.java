package com.goodsbridge.shop;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 입점 관리 달력(월별 조회)이 쓰는 입점 승인 시각 기록 규칙
class ShopApprovedAtTest {

    private Shop shop(ShopStatus status) {
        return Shop.builder().ownerUserId(1L).name("테스트점").region("서울").address("서울시").status(status).build();
    }

    @Test
    void pendingShop_hasNoApprovedAt() {
        assertThat(shop(ShopStatus.PENDING).getApprovedAt()).isNull();
    }

    @Test
    void approving_recordsApprovedAt() {
        Shop shop = shop(ShopStatus.PENDING);

        shop.updateStatus(ShopStatus.APPROVED);

        assertThat(shop.getApprovedAt()).isNotNull();
    }

    @Test
    void shopCreatedAsApproved_hasApprovedAt() {
        assertThat(shop(ShopStatus.APPROVED).getApprovedAt()).isNotNull();
    }

    @Test
    void cancellingApproval_clearsApprovedAt() {
        Shop shop = shop(ShopStatus.APPROVED);

        shop.updateStatus(ShopStatus.REJECTED);

        assertThat(shop.getApprovedAt()).isNull();
    }

    @Test
    void editingApprovedShop_sendsItBackToReviewAndClearsApprovedAt() {
        Shop shop = shop(ShopStatus.APPROVED);

        shop.update("바뀐 이름", "서울", "서울시");

        assertThat(shop.getStatus()).isEqualTo(ShopStatus.PENDING);
        assertThat(shop.getApprovedAt()).isNull();
    }
}
