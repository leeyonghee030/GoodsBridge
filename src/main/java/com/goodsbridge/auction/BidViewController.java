package com.goodsbridge.auction;

import com.goodsbridge._core.util.SecurityUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
@RequiredArgsConstructor
public class BidViewController {
    private final BidService bidService;

    // 입찰
    @PostMapping("/api/auctions/{id}/bids")
    public ResponseEntity<BidResponse> bids(@PathVariable Long id, @RequestBody BidRequest request){
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.status(201).body(bidService.placeBid(id, userId, request));
    }

    // 입찰 내역
    @GetMapping("/api/auctions/{id}/bids")
    public ResponseEntity<Page<BidResponse>> bidsList(@PathVariable Long id, Pageable pageable){
        return ResponseEntity.ok(bidService.getBids(id, pageable));
    }

    // 내가 참여한 경매
    @GetMapping("/api/auctions/me")
    public ResponseEntity<Page<AuctionResponse>> myBid(Pageable pageable){
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(bidService.participateAuction(userId, pageable));
    }
}
