package com.goodsbridge.auction;

import com.goodsbridge.item.Item;
import lombok.Getter;

import java.time.LocalDateTime;

// 가게가 등록한 경매 전용 상품 1건 (경매 상품 목록 · 수정 · 삭제용)
@Getter
public class AuctionItemResponse {
    private final Long id;
    private final Long shopId;
    private final Long categoryId;
    private final String categoryName;
    private final String title;
    private final String description;
    private final Integer price;        // 기준 가격 (경매 시작가 초안)
    private final String imageUrl;
    private final boolean inUse;        // 경매 중이거나 낙찰자의 잔금 결제를 기다리는 중 → 수정 · 삭제 · 새 경매 불가
    private final LocalDateTime createdAt;

    public AuctionItemResponse(Item item, boolean inUse) {
        this.id = item.getId();
        this.shopId = item.getShopId();
        this.categoryId = item.getCategory().getId();
        this.categoryName = item.getCategory().getName();
        this.title = item.getTitle();
        this.description = item.getDescription();
        this.price = item.getPrice();
        this.imageUrl = item.getImageUrl();
        this.inUse = inUse;
        this.createdAt = item.getCreatedAt();
    }
}
