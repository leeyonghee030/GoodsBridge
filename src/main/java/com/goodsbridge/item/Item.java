package com.goodsbridge.item;

import jakarta.persistence.*;
import lombok.*;
import org.checkerframework.checker.index.qual.Positive;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;


@Entity
@Table(name = "item_tb")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_user_id", nullable = false)
    private Long sellerUserId;

    @Column(name = "shop_id")
    private Long shopId; // null이면 개인 판매

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Positive
    @Column(nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleType saleType; // NORMAL, EVENT, AUCTION

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemStatus status; // ON_SALE, SOLD, CLOSED

    @Column(name = "image_url")
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Timestamp createdAt;

    @Builder
    public Item(Long sellerUserId, Long shopId, Category category, String title,
                String description, Integer price, SaleType saleType, ItemStatus status, String imageUrl) {
        this.sellerUserId = sellerUserId;
        this.shopId = shopId;
        this.category = category;
        this.title = title;
        this.description = description;
        this.price = price;
        this.saleType = saleType;
        this.status = status != null ? status : ItemStatus.ON_SALE;
        this.imageUrl = imageUrl;
    }

    public void update(Category category, String title, String description, Integer price, SaleType saleType, ItemStatus status, String imageUrl) {
        this.category = category;
        this.title = title;
        this.description = description;
        this.price = price;
        this.saleType = saleType;
        if (status != null) {
            this.status = status;
        }
        this.imageUrl = imageUrl;
    }

    public boolean isOwner(Long userId) {
        return this.sellerUserId.equals(userId);
    }
}
