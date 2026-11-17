package com.goodsbridge.auction;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 화면에서 사용할 경매 취소 API. /api/admin/** 보안 규칙으로 관리자만 접근한다. */
@RestController
@RequiredArgsConstructor
public class AdminAuctionViewController {
    private final AuctionService auctionService;

    @PatchMapping("/api/admin/auctions/{id}/cancel")
    public ResponseEntity<AuctionResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(auctionService.cancel(SecurityUtil.getCurrentUserId(), id));
    }
}
