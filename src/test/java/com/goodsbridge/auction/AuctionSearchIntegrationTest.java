package com.goodsbridge.auction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 경매 검색(탭 · 검색어 · 정렬 · 페이징 · 건수)이 실제 쿼리로 동작하는지 확인한다.
 * 테스트 데이터: 가게 1(주인 사용자 1)의 경매 전용 상품 5번('홍대 굿즈샵 시그니처 후드티').
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuctionSearchIntegrationTest {
    private static final Long OWNER_ID = 1L;
    private static final Long SHOP_ID = 1L;
    private static final Long AUCTION_ITEM_ID = 5L;

    @Autowired AuctionService auctionService;
    @Autowired AuctionSearchService searchService;

    private AuctionResponse openAuction() {
        AuctionRequest request = new AuctionRequest();
        ReflectionTestUtils.setField(request, "itemId", AUCTION_ITEM_ID);
        ReflectionTestUtils.setField(request, "startPrice", 50_000);
        ReflectionTestUtils.setField(request, "depositAmount", 5_000);
        ReflectionTestUtils.setField(request, "deadline", LocalDateTime.now().plusDays(1));
        return auctionService.create(OWNER_ID, SHOP_ID, request);
    }

    @Test
    void keywordSearchOnlyReturnsAuctionsInTheSelectedTab() {
        AuctionResponse opened = openAuction();

        Page<AuctionResponse> active = searchService.searchPublic(null, "ACTIVE", "시그니처", "DEADLINE", null, 0, 12);
        Page<AuctionResponse> ended = searchService.searchPublic(null, "ENDED", "시그니처", "DEADLINE", null, 0, 12);

        assertThat(active.getContent()).extracting(AuctionResponse::getId).contains(opened.getId());
        // 같은 검색어라도 종료 탭에서는 진행중 경매가 나오지 않는다
        assertThat(ended.getContent()).extracting(AuctionResponse::getId).doesNotContain(opened.getId());
    }

    @Test
    void searchesByShopNameToo_andNoMatchReturnsEmpty() {
        openAuction();

        assertThat(searchService.searchPublic(null, "ACTIVE", "홍대", "NEWEST", null, 0, 12).getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(searchService.searchPublic(null, "ACTIVE", "없는검색어zzz", "NEWEST", null, 0, 12).getTotalElements()).isZero();
    }

    @Test
    void everySortRunsAndPagingKeepsTotal() {
        openAuction();

        for (String sort : new String[]{"DEADLINE", "NEWEST", "PRICE_DESC", "PRICE_ASC", "BIDS"}) {
            Page<AuctionResponse> page = searchService.searchPublic(null, "ACTIVE", null, sort, null, 0, 1);
            assertThat(page.getContent()).hasSizeLessThanOrEqualTo(1);
            assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void shopCountsAndTabsFollowTheOwner() {
        AuctionResponse opened = openAuction();

        Map<String, Object> counts = searchService.shopCounts(OWNER_ID, null);
        Page<AuctionResponse> active = searchService.searchShop(OWNER_ID, "ACTIVE", "ALL", null, 0, 12);
        Page<AuctionResponse> other = searchService.searchShop(999L, "ACTIVE", "ALL", null, 0, 12);

        assertThat((Long) counts.get("ACTIVE")).isGreaterThanOrEqualTo(1);
        assertThat(active.getContent()).extracting(AuctionResponse::getId).contains(opened.getId());
        assertThat(((java.util.List<?>) counts.get("activeItemIds")).contains(AUCTION_ITEM_ID)).isTrue();
        // 다른 사람의 가게 경매는 보이지 않는다
        assertThat(other.getTotalElements()).isZero();
    }

    @Test
    void myCountsHaveEveryTab() {
        Map<String, Long> counts = searchService.mineCounts(2L, null);

        assertThat(counts.keySet()).containsExactlyElementsOf(AuctionSearchService.MINE_TABS);
    }
}
