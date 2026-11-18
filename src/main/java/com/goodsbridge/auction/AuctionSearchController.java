package com.goodsbridge.auction;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// 경매 화면용 서버 쪽 탭 · 검색 · 정렬 · 페이징 API (page 는 0부터, size 는 기본 12 최대 50)
@RestController
@RequiredArgsConstructor
public class AuctionSearchController {
    private final AuctionSearchService auctionSearchService;

    // 전체 경매 목록
    @GetMapping("/api/auctions/search")
    public ResponseEntity<Page<AuctionResponse>> search(@RequestParam(defaultValue = "ACTIVE") String tab,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(defaultValue = "DEADLINE") String sort,
                                                        @RequestParam(required = false) Long shopId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "12") int size) {
        Long viewerId = SecurityUtil.getCurrentUserIdOrNull();
        return ResponseEntity.ok(auctionSearchService.searchPublic(viewerId, tab, keyword, sort, shopId, page, size));
    }

    // 내 경매 내역
    @GetMapping("/api/auctions/me/search")
    public ResponseEntity<Page<AuctionResponse>> searchMine(@RequestParam(defaultValue = "BIDDING") String tab,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(auctionSearchService.searchMine(SecurityUtil.getCurrentUserId(), tab, keyword, page, size));
    }

    // 로그인한 사용자의 경매 참여 현황별 건수 조회
    @GetMapping("/api/auctions/me/counts")
    public ResponseEntity<Map<String, Long>> mineCounts(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(auctionSearchService.mineCounts(SecurityUtil.getCurrentUserId(), keyword));
    }

    // 가게 경매 관리
    @GetMapping("/api/shop-auctions/search")
    public ResponseEntity<Page<AuctionResponse>> searchShop(@RequestParam(defaultValue = "ACTIVE") String tab,
                                                            @RequestParam(defaultValue = "ALL") String sub,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(auctionSearchService.searchShop(SecurityUtil.getCurrentUserId(), tab, sub, keyword, page, size));
    }

    // 가게가 등록한 경매 전용 상품 목록
    @GetMapping("/api/shop-auctions/items")
    public ResponseEntity<Page<AuctionItemResponse>> shopItems(@RequestParam(required = false) String keyword,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(auctionSearchService.searchShopItems(SecurityUtil.getCurrentUserId(), keyword, page, size));
    }

    // 로그인한 판매자의 등록 경매 현황별 건수 조회
    @GetMapping("/api/shop-auctions/counts")
    public ResponseEntity<Map<String, Object>> shopCounts(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(auctionSearchService.shopCounts(SecurityUtil.getCurrentUserId(), keyword));
    }
}
