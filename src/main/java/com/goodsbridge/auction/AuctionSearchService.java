package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 경매 목록 화면(전체 목록 / 내 경매 내역 / 가게 경매 관리)의 서버 쪽 탭 · 검색 · 정렬 · 페이징.
 * 선택한 탭 안에서만 검색되고, 한 번에 size(최대 50)개만 가져오므로 데이터가 많아져도 응답 속도가 일정하다.
 * 정렬 기준은 허용 목록에서만 고르고(문자열 조립에 사용자 입력이 들어가지 않는다), 검색어는 파라미터로만 전달한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionSearchService {
    private static final String ACTIVE = "com.goodsbridge.auction.Status.ACTIVE";
    private static final String ENDED_SUCCESS = "com.goodsbridge.auction.Status.ENDED_SUCCESS";
    private static final String ENDED_FAIL = "com.goodsbridge.auction.Status.ENDED_FAIL";
    private static final String CANCELLED = "com.goodsbridge.auction.Status.CANCELLED";
    // 진행중이면서 마감 시간이 아직 안 지난 경매 (마감 처리 대기 중인 경매는 '끝난 것'으로 본다)
    private static final String LIVE = "(a.status = " + ACTIVE + " and a.deadline > :now)";

    private final EntityManager em;
    private final BidRepository bidRepository;

    // 조건 조립용: 조건 문자열과 바인딩할 값
    private static class Filter {
        final StringBuilder where = new StringBuilder("1 = 1");
        final Map<String, Object> params = new HashMap<>();

        Filter and(String condition) {
            where.append(" and ").append(condition);
            return this;
        }

        Filter param(String name, Object value) {
            params.put(name, value);
            return this;
        }
    }

    private static int clampSize(int size) {
        return Math.max(1, Math.min(size, 50));
    }

    private static Filter keyword(Filter filter, String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase().replace("%", "").replace("_", "");
        if (!kw.isEmpty()) {
            filter.and("(lower(i.title) like :kw or lower(s.name) like :kw)").param("kw", "%" + kw + "%");
        }
        return filter;
    }

    // ===================== 전체 경매 목록 (/test/auction) =====================

    // tab: ACTIVE(진행중) / ENDED(종료) / MINE(내 입찰). sort: DEADLINE / NEWEST / PRICE_DESC / PRICE_ASC / BIDS
    public Page<AuctionResponse> searchPublic(Long viewerId, String tab, String keyword, String sort, Long shopId, int page, int size) {
        Filter filter = keyword(new Filter(), keyword).param("now", LocalDateTime.now());
        switch (tab == null ? "ACTIVE" : tab) {
            case "ACTIVE" -> filter.and(LIVE);
            case "ENDED" -> filter.and("not " + LIVE);
            case "MINE" -> {
                if (viewerId == null) {
                    return new PageImpl<>(List.of(), PageRequest.of(Math.max(0, page), clampSize(size)), 0);
                }
                filter.and(participated()).param("uid", viewerId);
            }
            default -> throw new BadRequestException("올바르지 않은 탭입니다.");
        }
        if (shopId != null) {
            filter.and("s.id = :shopId").param("shopId", shopId);
        }
        String orderBy = switch (sort == null ? "DEADLINE" : sort) {
            // 진행중은 마감이 가까운 순, 끝난 경매는 최근에 끝난 순
            case "DEADLINE" -> "case when " + LIVE + " then 0 else 1 end asc, case when " + LIVE + " then a.deadline end asc, "
                + "case when " + LIVE + " then null else a.deadline end desc, a.id desc";
            case "NEWEST" -> "a.id desc";
            case "PRICE_DESC" -> "a.currentPrice desc, a.id desc";
            case "PRICE_ASC" -> "a.currentPrice asc, a.id desc";
            case "BIDS" -> "(select count(bb) from Bid bb where bb.auction = a) desc, a.deadline asc, a.id desc";
            default -> throw new BadRequestException("올바르지 않은 정렬입니다.");
        };
        return fetch(filter, orderBy, page, size, viewerId);
    }

    // ===================== 내 경매 내역 (/my/auctions) =====================

    public static final List<String> MINE_TABS = List.of("BIDDING", "WAIT", "PAID", "LOST", "FORFEIT", "CANCELLED");

    // 내가 입찰한 경매 중 이 탭에 해당하는 조건
    private static String mineTab(String tab) {
        return switch (tab) {
            case "BIDDING" -> "a.status = " + ACTIVE;
            // 낙찰 · 잔금 대기 / 결제 완료
            case "WAIT" -> "(a.status = " + ENDED_SUCCESS + " and a.currentWinner.id = :uid and a.balancePaymentId is null)";
            case "PAID" -> "(a.status = " + ENDED_SUCCESS + " and a.currentWinner.id = :uid and a.balancePaymentId is not null)";
            // 패찰: 다른 사람이 낙찰했거나, 낙찰자가 잔금을 안 내 무효가 된 경매의 다른 입찰자
            case "LOST" -> "((a.status = " + ENDED_SUCCESS + " or a.status = " + ENDED_FAIL + ") and (a.currentWinner is null or a.currentWinner.id <> :uid))";
            // 미결제: 내가 낙찰받고 기한 안에 잔금을 내지 않아 무효가 된 경매
            case "FORFEIT" -> "(a.status = " + ENDED_FAIL + " and a.currentWinner.id = :uid)";
            case "CANCELLED" -> "a.status = " + CANCELLED;
            default -> throw new BadRequestException("올바르지 않은 탭입니다.");
        };
    }

    private static String participated() {
        return "exists (select 1 from Bid mb where mb.auction = a and mb.user.id = :uid)";
    }

    private Filter mineFilter(Long userId, String keyword) {
        return keyword(new Filter(), keyword).and(participated()).param("uid", userId);
    }

    public Page<AuctionResponse> searchMine(Long userId, String tab, String keyword, int page, int size) {
        Filter filter = mineFilter(userId, keyword).and(mineTab(tab == null ? "BIDDING" : tab));
        return fetch(filter, "a.deadline desc, a.id desc", page, size, userId);
    }

    // 탭별 건수 (검색어가 있으면 검색 결과 기준)
    public Map<String, Long> mineCounts(Long userId, String keyword) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String tab : MINE_TABS) {
            counts.put(tab, count(mineFilter(userId, keyword).and(mineTab(tab))));
        }
        return counts;
    }

    // ===================== 가게 경매 관리 (/shop/auctions) =====================

    // sub(종료 탭의 분류): ALL / WAITING(낙찰 · 잔금 대기) / DONE(낙찰 · 정산 완료) / FORFEITED(낙찰자 미결제) / FAIL(유찰)
    private static String shopTab(String tab, String sub) {
        return switch (tab) {
            case "ACTIVE" -> "a.status = " + ACTIVE;
            case "CANCELLED" -> "a.status = " + CANCELLED;
            case "ENDED" -> switch (sub == null ? "ALL" : sub) {
                case "ALL" -> "(a.status = " + ENDED_SUCCESS + " or a.status = " + ENDED_FAIL + ")";
                case "WAITING" -> "(a.status = " + ENDED_SUCCESS + " and a.balancePaymentId is null)";
                case "DONE" -> "(a.status = " + ENDED_SUCCESS + " and a.balancePaymentId is not null)";
                case "FORFEITED" -> "(a.status = " + ENDED_FAIL + " and a.currentWinner is not null)";
                case "FAIL" -> "(a.status = " + ENDED_FAIL + " and a.currentWinner is null)";
                default -> throw new BadRequestException("올바르지 않은 분류입니다.");
            };
            default -> throw new BadRequestException("올바르지 않은 탭입니다.");
        };
    }

    private Filter shopFilter(Long ownerUserId, String keyword) {
        return keyword(new Filter(), keyword).and("s.ownerUserId = :uid and s.deleted = false").param("uid", ownerUserId);
    }

    public Page<AuctionResponse> searchShop(Long ownerUserId, String tab, String sub, String keyword, int page, int size) {
        String t = tab == null ? "ACTIVE" : tab;
        Filter filter = shopFilter(ownerUserId, keyword).and(shopTab(t, sub));
        // 진행중은 마감이 가까운 순, 나머지는 최근 순
        String orderBy = t.equals("ACTIVE") ? "a.deadline asc, a.id desc" : "a.deadline desc, a.id desc";
        return fetch(filter, orderBy, page, size, null);
    }

    // 탭 · 분류별 건수, 정산 예정(잔금 대기) 합계, 사용 중인 경매 상품 id (경매 열기에서 선택 불가로 표시하는 용도)
    public Map<String, Object> shopCounts(Long ownerUserId, String keyword) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ACTIVE", count(shopFilter(ownerUserId, keyword).and(shopTab("ACTIVE", null))));
        result.put("ENDED", count(shopFilter(ownerUserId, keyword).and(shopTab("ENDED", "ALL"))));
        result.put("CANCELLED", count(shopFilter(ownerUserId, keyword).and(shopTab("CANCELLED", null))));
        result.put("ITEMS", countShopItems(ownerUserId, keyword));
        for (String sub : List.of("WAITING", "DONE", "FORFEITED", "FAIL")) {
            result.put("ENDED_" + sub, count(shopFilter(ownerUserId, keyword).and(shopTab("ENDED", sub))));
        }
        // 정산 예정: 잔금 결제를 기다리는 낙찰의 낙찰가 합계 (검색어와 무관한 전체 기준)
        Filter waiting = shopFilter(ownerUserId, null).and(shopTab("ENDED", "WAITING"));
        Query sum = em.createQuery("select coalesce(sum(a.currentPrice), 0) from Auction a join a.item i join a.shop s where " + waiting.where);
        bind(sum, waiting.params, "select coalesce(sum(a.currentPrice), 0) from Auction a join a.item i join a.shop s where " + waiting.where);
        result.put("pendingAmount", ((Number) sum.getSingleResult()).longValue());
        result.put("pendingCount", count(waiting));
        // 사용 중인 상품: 진행 중인 경매의 상품 + 낙찰자의 잔금 결제를 기다리는 경매의 상품
        Filter active = shopFilter(ownerUserId, null)
            .and("(a.status = " + ACTIVE + " or (a.status = " + ENDED_SUCCESS + " and a.balancePaymentId is null))");
        String idsJpql = "select i.id from Auction a join a.item i join a.shop s where " + active.where;
        Query ids = em.createQuery(idsJpql);
        bind(ids, active.params, idsJpql);
        List<?> activeItemIds = ids.getResultList();
        result.put("activeItemIds", activeItemIds);
        return result;
    }

    // ===================== 가게의 경매 전용 상품 목록 (/shop/auctions '경매 상품' 탭) =====================

    private static final String ITEM_FROM = " from Item i join i.category c where i.saleType = com.goodsbridge.item.SaleType.AUCTION"
        + " and i.deleted = false and i.status = com.goodsbridge.item.ItemStatus.ON_SALE"
        + " and i.shopId in (select s.id from Shop s where s.ownerUserId = :uid and s.deleted = false)";

    // 아직 팔리지 않은 경매 전용 상품 (최근 등록순). inUse 가 true 면 경매 중이거나 낙찰자 잔금 결제 대기 중이라 수정 · 삭제 · 새 경매가 안 된다.
    public Page<AuctionItemResponse> searchShopItems(Long ownerUserId, String keyword, int page, int size) {
        int pageSize = clampSize(size);
        int pageIndex = Math.max(0, page);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase().replace("%", "").replace("_", "");
        String where = ITEM_FROM + (kw.isEmpty() ? "" : " and lower(i.title) like :kw");

        TypedQuery<Long> countQuery = em.createQuery("select count(i)" + where, Long.class);
        TypedQuery<com.goodsbridge.item.Item> dataQuery = em.createQuery(
            "select i" + where.replace(" join i.category c", " join fetch i.category c") + " order by i.id desc",
            com.goodsbridge.item.Item.class);
        for (Query q : List.<Query>of(countQuery, dataQuery)) {
            q.setParameter("uid", ownerUserId);
            if (!kw.isEmpty()) {
                q.setParameter("kw", "%" + kw + "%");
            }
        }
        long total = countQuery.getSingleResult();
        List<com.goodsbridge.item.Item> items = dataQuery.setFirstResult(pageIndex * pageSize).setMaxResults(pageSize).getResultList();

        Set<Long> inUse = new HashSet<>();
        if (!items.isEmpty()) {
            inUse.addAll(em.createQuery(
                    "select distinct a.item.id from Auction a where a.item.id in :ids and (a.status = " + ACTIVE
                        + " or (a.status = " + ENDED_SUCCESS + " and a.balancePaymentId is null))", Long.class)
                .setParameter("ids", items.stream().map(com.goodsbridge.item.Item::getId).toList()).getResultList());
        }
        List<AuctionItemResponse> content = items.stream().map(i -> new AuctionItemResponse(i, inUse.contains(i.getId()))).toList();
        return new PageImpl<>(content, PageRequest.of(pageIndex, pageSize), total);
    }

    // 경매 상품 탭 건수 (검색어 반영)
    private long countShopItems(Long ownerUserId, String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase().replace("%", "").replace("_", "");
        TypedQuery<Long> q = em.createQuery("select count(i)" + ITEM_FROM + (kw.isEmpty() ? "" : " and lower(i.title) like :kw"), Long.class);
        q.setParameter("uid", ownerUserId);
        if (!kw.isEmpty()) {
            q.setParameter("kw", "%" + kw + "%");
        }
        return q.getSingleResult();
    }

    // ===================== 공통 =====================

    private long count(Filter filter) {
        String jpql = "select count(a) from Auction a join a.item i join a.shop s where " + filter.where;
        TypedQuery<Long> query = em.createQuery(jpql, Long.class);
        bind(query, filter.params, jpql);
        return query.getSingleResult();
    }

    private Page<AuctionResponse> fetch(Filter filter, String orderBy, int page, int size, Long viewerId) {
        int pageSize = clampSize(size);
        int pageIndex = Math.max(0, page);
        String from = " from Auction a join a.item i join a.shop s where " + filter.where;

        String countJpql = "select count(a)" + from;
        TypedQuery<Long> countQuery = em.createQuery(countJpql, Long.class);
        bind(countQuery, filter.params, countJpql);
        long total = countQuery.getSingleResult();

        String dataJpql = "select a from Auction a join fetch a.item i left join fetch i.category join fetch a.shop s where "
            + filter.where + " order by " + orderBy;
        TypedQuery<Auction> dataQuery = em.createQuery(dataJpql, Auction.class);
        bind(dataQuery, filter.params, dataJpql);
        dataQuery.setFirstResult(pageIndex * pageSize).setMaxResults(pageSize);
        List<Auction> auctions = dataQuery.getResultList();

        List<Long> ids = auctions.stream().map(Auction::getId).toList();
        Map<Long, Long> bidCounts = new HashMap<>();
        Set<Long> participated = new HashSet<>();
        if (!ids.isEmpty()) {
            for (Object[] row : bidRepository.countBidsByAuctionIds(ids)) {
                bidCounts.put((Long) row[0], ((Number) row[1]).longValue());
            }
            if (viewerId != null) {
                participated.addAll(em.createQuery(
                        "select distinct b.auction.id from Bid b where b.user.id = :uid and b.auction.id in :ids", Long.class)
                    .setParameter("uid", viewerId).setParameter("ids", ids).getResultList());
            }
        }
        List<AuctionResponse> content = new ArrayList<>();
        for (Auction auction : auctions) {
            content.add(new AuctionResponse(auction)
                .withBidCount(bidCounts.getOrDefault(auction.getId(), 0L))
                .withParticipated(participated.contains(auction.getId())));
        }
        return new PageImpl<>(content, PageRequest.of(pageIndex, pageSize), total);
    }

    // 쿼리에 실제로 들어 있는 이름의 값만 바인딩한다 (정렬에만 쓰는 값이 개수 쿼리에서 빠질 수 있어서)
    private static void bind(Query query, Map<String, Object> params, String jpql) {
        params.forEach((name, value) -> {
            if (jpql.contains(":" + name)) {
                query.setParameter(name, value);
            }
        });
    }
}
