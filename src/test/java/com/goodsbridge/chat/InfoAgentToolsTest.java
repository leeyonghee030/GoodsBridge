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
    void searchItems_usesTheSameShopOnlyOnSaleQueryAsTheProductPage() {
        List<ItemResponse> found = new ArrayList<>();
        for (long id = 2; id <= 4; id++) {
            found.add(item(id, "포토카드 " + id, ItemStatus.ON_SALE));
        }
        when(itemService.findShopItems(eq(null), eq(null), eq(null), eq("포토카드"), eq(ItemStatus.ON_SALE), any(Pageable.class)))
            .thenReturn(new PageImpl<>(found));

        InfoAgentTools.ItemSearchResult result = tools.searchItems("포토카드", context);

        // 상품 화면과 같은 조회(가게 상품만, 판매 중만)를 쓴다. 사용자의 교환 전용·경매·이벤트 상품이 섞이지 않는다.
        assertThat(result.items()).extracting(InfoAgentTools.ItemInfo::itemId).containsExactly(2L, 3L, 4L);
        assertThat(result.items().get(0).link()).isEqualTo("/test/item/2");
        assertThat(notes).containsExactly("상품 검색 '포토카드': 3건");
        verify(itemService, org.mockito.Mockito.never()).findAll(any(), any(), any(), any(), any(Pageable.class));
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

    private AuctionResponse auction(long id, Status status, Long winnerId, boolean balancePaid, LocalDateTime balanceDueAt) {
        AuctionResponse a = mock(AuctionResponse.class);
        when(a.getId()).thenReturn(id);
        when(a.getStatus()).thenReturn(status);
        when(a.getCurrentWinnerUserId()).thenReturn(winnerId);
        when(a.isBalancePaid()).thenReturn(balancePaid);
        when(a.getBalanceDueAt()).thenReturn(balanceDueAt);
        when(a.getItemTitle()).thenReturn("경매 상품");
        when(a.getShopName()).thenReturn("데모 굿즈샵");
        when(a.getCurrentPrice()).thenReturn(30000);
        when(a.getDeadline()).thenReturn(LocalDateTime.now().minusDays(1));
        return a;
    }

    @Test
    void getMyAuctions_flagsABalancePaymentOnlyForMyUnpaidWinBeforeTheDeadline() {
        LocalDateTime future = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0);
        List<AuctionResponse> mine = List.of(
            auction(1L, Status.ENDED_SUCCESS, USER_ID, false, future),                              // 내가 낙찰, 잔금 아직
            auction(2L, Status.ENDED_SUCCESS, USER_ID, true, future),                               // 내가 낙찰, 잔금 완료
            auction(3L, Status.ENDED_SUCCESS, 99L, false, future),                                 // 남이 낙찰
            auction(4L, Status.ACTIVE, USER_ID, false, null),                                       // 진행 중, 내가 최고 입찰
            auction(5L, Status.ENDED_SUCCESS, USER_ID, false, LocalDateTime.now().minusHours(1)),   // 내가 낙찰했지만 기한이 지남
            auction(6L, Status.CANCELLED, USER_ID, false, null));                                   // 취소된 경매에 남은 입찰자 정보
        when(bidService.participateAuction(eq(USER_ID), any(Pageable.class))).thenReturn(new PageImpl<>(mine));

        List<InfoAgentTools.AuctionInfo> result = tools.getMyAuctions(context).auctions();

        assertThat(result).extracting(InfoAgentTools.AuctionInfo::needsBalancePayment)
            .containsExactly(true, false, false, false, false, false);
        // 기한이 지난 낙찰은 결제하라고 안내하지 않고 "기한 지남"으로 알린다
        assertThat(result).extracting(InfoAgentTools.AuctionInfo::balanceOverdue)
            .containsExactly(false, false, false, false, true, false);
        assertThat(result.get(0).balanceDueAt()).isEqualTo(future.toString().replace('T', ' ').substring(0, 16));
        assertThat(result.get(1).balanceDueAt()).isNull();
        // 취소된 경매는 내가 마지막 입찰자였어도 "앞서 있다"고 하지 않는다
        assertThat(result).extracting(InfoAgentTools.AuctionInfo::iAmTopBidder)
            .containsExactly(true, true, false, true, true, false);
        assertThat(result.get(0).status()).isEqualTo("낙찰");
        assertThat(result.get(3).status()).isEqualTo("경매 중");
        assertThat(result.get(0).link()).isEqualTo("/test/auction/1");
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
