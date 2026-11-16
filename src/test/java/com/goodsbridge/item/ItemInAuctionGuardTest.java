package com.goodsbridge.item;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.auction.AuctionRequest;
import com.goodsbridge.auction.AuctionResponse;
import com.goodsbridge.auction.AuctionSearchService;
import com.goodsbridge.auction.AuctionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 경매 중인(또는 낙찰자 잔금 결제를 기다리는) 상품은 수정·삭제할 수 없다.
 * 테스트 데이터: 가게 1(주인 사용자 1)의 경매 전용 상품 5번.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ItemInAuctionGuardTest {
    private static final Long OWNER_ID = 1L;
    private static final Long SHOP_ID = 1L;
    private static final Long ITEM_ID = 5L;

    @Autowired ItemService itemService;
    @Autowired AuctionService auctionService;
    @Autowired AuctionSearchService searchService;

    private AuctionResponse openAuction() {
        AuctionRequest request = new AuctionRequest();
        ReflectionTestUtils.setField(request, "itemId", ITEM_ID);
        ReflectionTestUtils.setField(request, "startPrice", 50_000);
        ReflectionTestUtils.setField(request, "depositAmount", 5_000);
        ReflectionTestUtils.setField(request, "deadline", LocalDateTime.now().plusDays(1));
        return auctionService.create(OWNER_ID, SHOP_ID, request);
    }

    private ItemUpdateRequest update(SaleType saleType, int price) {
        ItemUpdateRequest request = new ItemUpdateRequest();
        request.setShopId(SHOP_ID);
        request.setCategoryId(2L);
        request.setTitle("바꾼 제목");
        request.setDescription("바꾼 설명");
        request.setPrice(price);
        request.setSaleType(saleType);
        request.setStatus(ItemStatus.ON_SALE);
        return request;
    }

    @Test
    void itemInActiveAuctionCannotBeEditedOrDeleted() {
        openAuction();

        assertThatThrownBy(() -> itemService.update(OWNER_ID, ITEM_ID, update(SaleType.NORMAL, 1_000)))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("경매");
        assertThatThrownBy(() -> itemService.delete(OWNER_ID, ITEM_ID))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("경매");
    }

    @Test
    void itemNotInAuctionCanBeEditedAndDeleted() {
        itemService.update(OWNER_ID, ITEM_ID, update(SaleType.AUCTION, 40_000));
        assertThat(itemService.findById(ITEM_ID).getTitle()).isEqualTo("바꾼 제목");

        itemService.delete(OWNER_ID, ITEM_ID);
        assertThatThrownBy(() -> itemService.findById(ITEM_ID)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void auctionItemListShowsInUseFlag() {
        var before = searchService.searchShopItems(OWNER_ID, null, 0, 12);
        assertThat(before.getContent()).anyMatch(i -> i.getId().equals(ITEM_ID) && !i.isInUse());

        openAuction();

        var after = searchService.searchShopItems(OWNER_ID, "시그니처", 0, 12);
        assertThat(after.getContent()).anyMatch(i -> i.getId().equals(ITEM_ID) && i.isInUse());
        // 다른 사람의 경매 상품은 보이지 않는다
        assertThat(searchService.searchShopItems(999L, null, 0, 12).getTotalElements()).isZero();
    }
}
