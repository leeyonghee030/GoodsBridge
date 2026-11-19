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
    private EventParticipationResponse participation(String title, EventParticipationStatus status, LocalDateTime createdAt) {
        EventParticipationResponse e = mock(EventParticipationResponse.class);
        when(e.getEventTitle()).thenReturn(title);
        when(e.getStatus()).thenReturn(status);
        when(e.getCreatedAt()).thenReturn(createdAt);
        return e;
    }

    @Test
    void getMyEvents_givesCountsPerStatusAndOnlyTheThreeNewestInsteadOfALongList() {
        LocalDateTime now = LocalDateTime.now();
        List<EventParticipationResponse> mine = List.of(
            participation("가장 오래된", EventParticipationStatus.JOINED, now.minusDays(4)),
            participation("셋째", EventParticipationStatus.CANCELLED, now.minusDays(2)),
            participation("최신", EventParticipationStatus.JOINED, now),
            participation("둘째", EventParticipationStatus.REFUNDED, now.minusDays(1)),
            participation("넷째", EventParticipationStatus.JOINED, now.minusDays(3)));
        when(eventService.getEventParticipationList(USER_ID)).thenReturn(mine);

        InfoAgentTools.MyEventsResult result = tools.getMyEvents(context);

        // 채팅에는 건수 요약과 최근 3건만 주고, 자세한 내용은 화면에서 보게 한다
        assertThat(result.totalCount()).isEqualTo(5);
        assertThat(result.countsByStatus()).containsEntry("참여", 3L).containsEntry("취소", 1L).containsEntry("환불", 1L);
        assertThat(result.recent()).extracting(InfoAgentTools.EventBrief::title).containsExactly("최신", "둘째", "셋째");
        assertThat(result.recent().get(1).status()).isEqualTo("환불");
        assertThat(result.allLink()).isEqualTo("/my/events");
        verify(eventService).getEventParticipationList(USER_ID);   // 사용자 id는 서버가 넣은 값만 쓴다
        assertThat(notes).containsExactly("내 이벤트 조회: 5건");
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

        InfoAgentTools.MyAuctionsResult result = tools.getMyAuctions(context);

        // 요약: 상태별 건수와 "잔금을 내야 하는 건수"(내가 낙찰했고 아직 안 냈고 기한 전인 것만)
        assertThat(result.totalCount()).isEqualTo(6);
        assertThat(result.needsBalancePaymentCount()).isEqualTo(1);
        // 내 입장의 상태: 내가 낙찰한 건 "낙찰", 남이 낙찰한 건 "낙찰 실패"다. 진행 중은 "경매 중"으로 하나로 센다.
        assertThat(result.countsByStatus()).containsEntry("낙찰", 3L).containsEntry("낙찰 실패", 1L)
            .containsEntry("경매 중", 1L).containsEntry("취소", 1L);
        // 최근 3건만 준다
        assertThat(result.recent()).hasSize(3);
        assertThat(result.recent().get(0).needsBalancePayment()).isTrue();
        assertThat(result.recent().get(0).balanceDueAt()).isEqualTo(future.toString().replace('T', ' ').substring(0, 16));
        assertThat(result.recent().get(1).needsBalancePayment()).isFalse();   // 잔금을 이미 냈다
        assertThat(result.recent().get(1).balanceDueAt()).isNull();
        assertThat(result.recent().get(2).status()).isEqualTo("낙찰 실패");
        assertThat(result.allLink()).isEqualTo("/my/auctions");
    }

    @Test
    void getMyAuctions_saysThePaymentDeadlinePassedInsteadOfAskingToPay() {
        AuctionResponse expired = auction(5L, Status.ENDED_SUCCESS, USER_ID, false, LocalDateTime.now().minusHours(1));
        when(bidService.participateAuction(eq(USER_ID), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(expired)));

        InfoAgentTools.MyAuctionsResult result = tools.getMyAuctions(context);

        // 기한이 지난 낙찰은 결제하라고 안내하지 않는다 (더는 결제할 수 없다)
        assertThat(result.needsBalancePaymentCount()).isZero();
        assertThat(result.recent().get(0).needsBalancePayment()).isFalse();
        assertThat(result.recent().get(0).balanceOverdue()).isTrue();
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
