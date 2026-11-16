package com.goodsbridge.chat;

import com.goodsbridge.auction.AuctionResponse;
import com.goodsbridge.auction.BidService;
import com.goodsbridge.auction.Status;
import com.goodsbridge.event.EventService;
import com.goodsbridge.event.dto.EventParticipationResponse;
import com.goodsbridge.item.ItemResponse;
import com.goodsbridge.item.ItemService;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.shop.ShopResponse;
import com.goodsbridge.shop.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI가 쓰는 "정보 조회" 도구 모음. 가게·상품 검색과 내 이벤트·경매 현황을 읽기만 한다 (데이터를 바꾸지 않는다).
 *
 * <p>[왜 따로 만들었나] 교환 도구({@link ExchangeAgentTools})는 취소 같은 쓰기가 있어 이중 확인 규칙이 붙는다.
 * 이 클래스는 읽기 전용이라 확인 단계가 필요 없고, 서비스 계층을 직접 호출한다 (REST로 노출하지 않는다).
 *
 * <p>[토큰 절약·간단한 답] 검색은 항목마다 링크가 필요해서 최대 {@value #MAX_SEARCH}건을 돌려준다. 반면 "내 이벤트·내 경매"는
 * 채팅에서 길게 나열하지 않고 <b>건수 요약과 최근 {@value #RECENT}건만</b> 주고, 자세한 내용은 화면(allLink)에서 보게 한다.
 * 로그인한 사용자 id는 AI가 넘기는 값이 아니라 서버가 {@link ToolContext}로 넣어 준다.
 */
@Component
@RequiredArgsConstructor
public class InfoAgentTools {

    private static final int MAX_SEARCH = 5;
    private static final int RECENT = 3;
    private static final int AUCTION_WINDOW = 50;   // 상태별 건수를 세려고 한 번에 가져오는 최근 경매 수

    private final ShopService shopService;
    private final ItemService itemService;
    private final EventService eventService;
    private final BidService bidService;

    public record ShopInfo(Long shopId, String name, String region, String address, String link) {
    }

    public record ShopSearchResult(int shownCount, long totalCount, List<ShopInfo> shops, String listLink) {
    }

    public record ItemInfo(Long itemId, String title, String category, Integer price, String saleType, String link) {
    }

    public record ItemSearchResult(int shownCount, List<ItemInfo> items, String listLink) {
    }

    public record EventBrief(String title, String status) {
    }

    public record MyEventsResult(int totalCount, Map<String, Long> countsByStatus, List<EventBrief> recent, String allLink) {
    }

    public record AuctionBrief(String title, String status, boolean needsBalancePayment, boolean balanceOverdue,
                               String balanceDueAt) {
    }

    public record MyAuctionsResult(long totalCount, Map<String, Long> countsByStatus, long needsBalancePaymentCount,
                                   List<AuctionBrief> recent, String allLink) {
    }

    @Tool(description = "입점한 가게를 이름이나 지역으로 검색한다. 사용자가 어떤 가게가 있는지, 특정 지역의 가게, 가게 이름으로 찾기를 물을 때 사용한다. "
        + "이름과 지역 둘 다 생략하면 전체 가게 중 일부를 돌려준다. 결과의 link를 답변에 붙여 준다.")
    public ShopSearchResult searchShops(
        @ToolParam(required = false, description = "가게 이름에 들어 있는 글자. 예: 데모") String name,
        @ToolParam(required = false, description = "지역(시·도 이름 그대로 일치해야 한다). 예: 서울, 부산") String region,
        ToolContext toolContext) {
        var page = shopService.findAll(blankToNull(name), blankToNull(region), PageRequest.of(0, MAX_SEARCH));
        List<ShopInfo> shops = page.getContent().stream().map(this::toShopInfo).toList();
        note(toolContext, "가게 검색: " + shops.size() + "곳");
        return new ShopSearchResult(shops.size(), page.getTotalElements(), shops, "/test/shop");
    }

    @Tool(description = "판매 중인 상품을 이름(제목)으로 검색한다. 사용자가 어떤 상품이 있는지, 특정 굿즈를 파는지 물을 때 사용한다. "
        + "결과의 link를 답변에 붙여 준다. 결과가 없으면 없다고 말하고 상품 목록 링크를 안내한다.")
    public ItemSearchResult searchItems(
        @ToolParam(description = "상품 이름에 들어 있는 검색어. 예: 포토카드, 응원봉") String keyword,
        ToolContext toolContext) {
        // 상품 화면(/test/item)과 같은 조회를 쓴다: 가게가 파는 일반 상품만 (사용자의 교환 전용 상품, 경매·이벤트 상품은 제외).
        // 판매 중인 것만 조건으로 걸어서 판매 완료·닫힌 상품에 밀려 결과가 빠지는 일이 없게 한다.
        List<ItemInfo> items = itemService.findShopItems(null, null, null, keyword, ItemStatus.ON_SALE, PageRequest.of(0, MAX_SEARCH))
            .getContent().stream()
            .map(this::toItemInfo)
            .toList();
        note(toolContext, "상품 검색 '" + abbreviate(keyword) + "': " + items.size() + "건");
        return new ItemSearchResult(items.size(), items, "/test/item");
    }

    @Tool(description = "로그인한 사용자의 이벤트 참여 현황을 요약해서 조회한다(상태별 건수와 최근 3건). 사용자가 내 이벤트, 이벤트 참여 현황, "
        + "내 번호표를 물을 때 사용한다. 자세한 내용은 allLink 화면에서 보도록 안내한다.")
    public MyEventsResult getMyEvents(ToolContext toolContext) {
        Long userId = userId(toolContext);
        List<EventParticipationResponse> all = eventService.getEventParticipationList(userId).stream()
            .sorted(Comparator.comparing(EventParticipationResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        all.forEach(e -> counts.merge(eventStatusLabel(e), 1L, Long::sum));
        List<EventBrief> recent = all.stream().limit(RECENT)
            .map(e -> new EventBrief(e.getEventTitle(), eventStatusLabel(e))).toList();
        note(toolContext, "내 이벤트 조회: " + all.size() + "건");
        return new MyEventsResult(all.size(), counts, recent, "/my/events");
    }

    @Tool(description = "로그인한 사용자의 경매 입찰 현황을 요약해서 조회한다(상태별 건수, 잔금 결제가 필요한 건수, 최근 3건). 사용자가 내 경매, "
        + "입찰 현황, 낙찰 여부, 잔금 결제를 물을 때 사용한다. needsBalancePaymentCount가 0보다 크면 잔금을 결제해야 한다고 먼저 알려 주고, "
        + "recent의 balanceOverdue가 true면 결제 기한이 지났다고 알린다. 자세한 내용은 allLink 화면에서 보도록 안내한다.")
    public MyAuctionsResult getMyAuctions(ToolContext toolContext) {
        Long userId = userId(toolContext);
        var page = bidService.participateAuction(userId, PageRequest.of(0, AUCTION_WINDOW));
        List<AuctionBrief> all = page.getContent().stream().map(a -> toAuctionBrief(a, userId)).toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        all.forEach(a -> counts.merge(a.status(), 1L, Long::sum));
        long needsBalance = all.stream().filter(AuctionBrief::needsBalancePayment).count();
        note(toolContext, "내 경매 조회: " + page.getTotalElements() + "건");
        return new MyAuctionsResult(page.getTotalElements(), counts, needsBalance, all.stream().limit(RECENT).toList(), "/my/auctions");
    }

    private ShopInfo toShopInfo(ShopResponse s) {
        return new ShopInfo(s.getId(), s.getName(), s.getRegion(), s.getAddress(), "/test/shop/" + s.getId());
    }

    private ItemInfo toItemInfo(ItemResponse i) {
        return new ItemInfo(i.getId(), i.getTitle(), i.getCategoryName(), i.getPrice(),
            i.getSaleType() == null ? null : i.getSaleType().name(), "/test/item/" + i.getId());
    }

    private String eventStatusLabel(EventParticipationResponse e) {
        return e.getStatus() == null ? "알 수 없음" : e.getStatus().getDisplayName();
    }

    private AuctionBrief toAuctionBrief(AuctionResponse a, Long userId) {
        // 최고 입찰자(진행 중) 또는 낙찰자(낙찰)일 때만 내 낙찰로 본다. 취소·유찰된 경매에 남은 입찰자 정보는 의미가 없다.
        boolean mine = userId.equals(a.getCurrentWinnerUserId())
            && (a.getStatus() == Status.ACTIVE || a.getStatus() == Status.ENDED_SUCCESS);
        boolean unpaidWin = mine && a.getStatus() == Status.ENDED_SUCCESS && !a.isBalancePaid();
        // 잔금 기한이 지났으면 더는 결제할 수 없다 (결제하라고 안내하면 안 된다)
        boolean overdue = unpaidWin && a.getBalanceDueAt() != null && LocalDateTime.now().isAfter(a.getBalanceDueAt());
        return new AuctionBrief(a.getItemTitle(), auctionStatusLabel(a, mine), unpaidWin && !overdue, overdue,
            unpaidWin ? format(a.getBalanceDueAt()) : null);
    }

    // 내 입장에서 읽는 상태. 낙찰이면 내가 낙찰했는지 놓쳤는지를 나눈다. 진행 중은 그냥 "경매 중"이다
    // ("경매 중(최고 입찰)"처럼 괄호를 붙이면 AI가 한 상태를 둘로 쪼개서 요약했다).
    private String auctionStatusLabel(AuctionResponse a, boolean mine) {
        if (a.getStatus() == null) {
            return "알 수 없음";
        }
        return switch (a.getStatus()) {
            case ACTIVE -> "경매 중";
            case ENDED_SUCCESS -> mine ? "낙찰" : "낙찰 실패";
            case ENDED_FAIL -> "유찰";
            case CANCELLED -> "취소";
        };
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.toString().replace('T', ' ').substring(0, 16);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 20 ? value.substring(0, 20) + "…" : value;
    }

    @SuppressWarnings("unchecked")
    private void note(ToolContext toolContext, String text) {
        Object notes = toolContext.getContext().get(ChatService.TOOL_NOTES_KEY);
        if (notes instanceof List<?> list) {
            ((List<String>) list).add(text);
        }
    }

    private Long userId(ToolContext toolContext) {
        Object id = toolContext.getContext().get(ChatService.USER_ID_KEY);
        if (!(id instanceof Long userId)) {
            throw new IllegalStateException("도구를 실행할 사용자 정보가 없습니다.");
        }
        return userId;
    }
}
