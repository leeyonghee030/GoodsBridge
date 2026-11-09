package com.goodsbridge.auction;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class AuctionViewController {
    private final AuctionService auctionService;

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.channel-key}")
    private String channelKey;

    @GetMapping("/test/auction")
    public String index(Model model) {
        model.addAttribute("auctions", auctionService.findAll(1, 60, null).getContent());
        model.addAttribute("portoneStoreId", storeId);
        model.addAttribute("portoneChannelKey", channelKey);
        model.addAttribute("userId", SecurityUtil.getCurrentUserIdOrNull());
        return "auction/index";
    }

    // 경매 개설
    @PostMapping("/api/shops/{id}/auctions")
    public ResponseEntity<AuctionResponse> auctionOpening(@PathVariable Long id,
                                                          @RequestBody AuctionRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        AuctionResponse auctionResponse = auctionService.create(userId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(auctionResponse);
    }

    // 경매 목록
    @GetMapping("/api/auctions")
    public ResponseEntity<Page<AuctionResponse>> findAll(@RequestParam(name = "page", defaultValue = "1") int page,
                                                         @RequestParam(name = "size", defaultValue = "3") int size,
                                                         @RequestParam(required = false) Status status) {
        return ResponseEntity.ok(auctionService.findAll(page, size, status));
    }

    // 경매 상세
    @GetMapping("/api/auctions/{id}")
    public ResponseEntity<AuctionResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(auctionService.findById(id));
    }

    // 경매 마감
    @PatchMapping("/api/auctions/{id}/close")
    public ResponseEntity<AuctionResponse> closeAuction(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        AuctionResponse auctionResponse = auctionService.close(userId, id);
        return ResponseEntity.ok(auctionResponse);
    }
}
