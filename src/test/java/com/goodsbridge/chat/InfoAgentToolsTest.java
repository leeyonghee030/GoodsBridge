package com.goodsbridge.chat;

import com.goodsbridge.auction.AuctionResponse;
import com.goodsbridge.auction.BidService;
import com.goodsbridge.auction.Status;
import com.goodsbridge.event.EventService;
import com.goodsbridge.event.EventParticipationStatus;
import com.goodsbridge.event.dto.EventParticipationResponse;
import com.goodsbridge.item.ItemResponse;
import com.goodsbridge.item.ItemService;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.shop.ShopResponse;
import com.goodsbridge.shop.ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)   // 응답 DTO 목은 테스트마다 쓰는 값이 달라서
class InfoAgentToolsTest {

    private static final Long USER_ID = 7L;

    @Mock
    private ShopService shopService;
    @Mock
    private ItemService itemService;
    @Mock
    private EventService eventService;
    @Mock
    private BidService bidService;

    private InfoAgentTools tools;
    private List<String> notes;
    private ToolContext context;

    @BeforeEach
    void setUp() {
        tools = new InfoAgentTools(shopService, itemService, eventService, bidService);
        notes = new CopyOnWriteArrayList<>();
        context = new ToolContext(Map.of(ChatService.USER_ID_KEY, USER_ID, ChatService.TOOL_NOTES_KEY, notes));
    }

    private ShopResponse shop(long id, String name, String region) {
        ShopResponse s = mock(ShopResponse.class);
        when(s.getId()).thenReturn(id);
        when(s.getName()).thenReturn(name);
        when(s.getRegion()).thenReturn(region);
        when(s.getAddress()).thenReturn(region + " 어딘가");
        return s;
    }

    private ItemResponse item(long id, String title, ItemStatus status) {
        ItemResponse i = mock(ItemResponse.class);
        when(i.getStatus()).thenReturn(status);
        if (status == ItemStatus.ON_SALE) {
            when(i.getId()).thenReturn(id);
            when(i.getTitle()).thenReturn(title);
            when(i.getCategoryName()).thenReturn("포토카드");
            when(i.getPrice()).thenReturn(5000);
        }
        return i;
    }

    @Test
    void searchShops_returnsShopsWithLinksAndTrimsBlankFilters() {
        ShopResponse demo = shop(3L, "데모 굿즈샵", "서울");
        when(shopService.findAll(eq("데모"), eq(null), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(demo)));

        InfoAgentTools.ShopSearchResult result = tools.searchShops(" 데모 ", "  ", context);

        assertThat(result.shops()).hasSize(1);
        assertThat(result.shops().get(0).link()).isEqualTo("/test/shop/3");
        assertThat(result.listLink()).isEqualTo("/test/shop");
        assertThat(notes).containsExactly("가게 검색: 1곳");
    }

    @Test
    void searchItems_showsOnlyOnSaleItemsUpToFiveWithLinks() {
        List<ItemResponse> found = new ArrayList<>();
        found.add(item(1L, "판매 완료 상품", ItemStatus.SOLD));
        for (long id = 2; id <= 8; id++) {
            found.add(item(id, "포토카드 " + id, ItemStatus.ON_SALE));
        }
        when(itemService.findAll(eq(null), eq(null), eq(null), eq("포토카드"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(found));

        InfoAgentTools.ItemSearchResult result = tools.searchItems("포토카드", context);

        // 판매 완료는 빼고, 최대 5건만 돌려준다 (토큰 절약)
        assertThat(result.items()).hasSize(5);
        assertThat(result.items()).extracting(InfoAgentTools.ItemInfo::itemId).containsExactly(2L, 3L, 4L, 5L, 6L);
        assertThat(result.items().get(0).link()).isEqualTo("/test/item/2");
        assertThat(notes).containsExactly("상품 검색 '포토카드': 5건");
    }

    @Test
    void getMyEvents_usesTheLoggedInUserAndLinksToTheEvent() {
        EventParticipationResponse e = mock(EventParticipationResponse.class);
        when(e.getShopEventId()).thenReturn(11L);
        when(e.getEventTitle()).thenReturn("선착순 이벤트");
        when(e.getShopName()).thenReturn("데모 굿즈샵");
        when(e.getStatus()).thenReturn(EventParticipationStatus.JOINED);
        when(e.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(eventService.getEventParticipationList(USER_ID)).thenReturn(List.of(e));

        InfoAgentTools.MyEventsResult result = tools.getMyEvents(context);

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.events().get(0).status()).isEqualTo("참여");
        assertThat(result.events().get(0).link()).isEqualTo("/test/event/11");
        assertThat(result.allLink()).isEqualTo("/my/events");
        verify(eventService).getEventParticipationList(USER_ID);   // 사용자 id는 서버가 넣은 값만 쓴다
    }

    private AuctionResponse auction(long id, Status status, Long winnerId, boolean balancePaid) {
        AuctionResponse a = mock(AuctionResponse.class);
        when(a.getId()).thenReturn(id);
        when(a.getStatus()).thenReturn(status);
        when(a.getCurrentWinnerUserId()).thenReturn(winnerId);
        when(a.isBalancePaid()).thenReturn(balancePaid);
        lenientAuction(a);
        return a;
    }

    private void lenientAuction(AuctionResponse a) {
        org.mockito.Mockito.lenient().when(a.getItemTitle()).thenReturn("경매 상품");
        org.mockito.Mockito.lenient().when(a.getShopName()).thenReturn("데모 굿즈샵");
        org.mockito.Mockito.lenient().when(a.getCurrentPrice()).thenReturn(30000);
        org.mockito.Mockito.lenient().when(a.getDeadline()).thenReturn(LocalDateTime.of(2026, 11, 20, 18, 0));
        org.mockito.Mockito.lenient().when(a.getBalanceDueAt()).thenReturn(LocalDateTime.of(2026, 11, 21, 18, 0));
    }

    @Test
    void getMyAuctions_flagsABalancePaymentOnlyForMyUnpaidWin() {
        List<AuctionResponse> mine = List.of(
            auction(1L, Status.ENDED_SUCCESS, USER_ID, false),   // 내가 낙찰, 잔금 아직
            auction(2L, Status.ENDED_SUCCESS, USER_ID, true),    // 내가 낙찰, 잔금 완료
            auction(3L, Status.ENDED_SUCCESS, 99L, false),       // 남이 낙찰
            auction(4L, Status.ACTIVE, USER_ID, false));         // 진행 중, 내가 최고 입찰
        when(bidService.participateAuction(eq(USER_ID), any(Pageable.class))).thenReturn(new PageImpl<>(mine));

        InfoAgentTools.MyAuctionsResult result = tools.getMyAuctions(context);

        assertThat(result.auctions()).extracting(InfoAgentTools.AuctionInfo::needsBalancePayment)
            .containsExactly(true, false, false, false);
        assertThat(result.auctions().get(0).balanceDueAt()).isEqualTo("2026-11-21 18:00");
        assertThat(result.auctions().get(1).balanceDueAt()).isNull();
        assertThat(result.auctions()).extracting(InfoAgentTools.AuctionInfo::iAmTopBidder)
            .containsExactly(true, true, false, true);
        assertThat(result.auctions().get(0).status()).isEqualTo("낙찰");
        assertThat(result.auctions().get(3).status()).isEqualTo("경매 중");
        assertThat(result.auctions().get(0).link()).isEqualTo("/test/auction/1");
    }

    @Test
    void myToolsRefuseToRunWithoutAServerProvidedUser() {
        ToolContext noUser = new ToolContext(Map.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tools.getMyEvents(noUser))
            .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tools.getMyAuctions(noUser))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void springAi_exposesFourReadOnlyToolsAndNeverTheUserId() {
        var callbacks = org.springframework.ai.support.ToolCallbacks.from(tools);

        assertThat(callbacks).extracting(c -> c.getToolDefinition().name())
            .containsExactlyInAnyOrder("searchShops", "searchItems", "getMyEvents", "getMyAuctions");
        for (var callback : callbacks) {
            // 로그인한 사용자 id는 AI가 넘기는 값이 아니라 서버가 넣어 준다 (AI에게 보이는 입력에 없다)
            assertThat(callback.getToolDefinition().inputSchema()).doesNotContain("userId");
        }
    }
}
