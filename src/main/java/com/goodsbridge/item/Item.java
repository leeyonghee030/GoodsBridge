package com.goodsbridge.item;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp; // 제거 가능
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime; // 변경

@Entity
@Table(name = "item_tb")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class) // ⭐ 추가
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_user_id", nullable = false)
    private Long sellerUserId;

    @Column(name = "shop_id")
    private Long shopId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleType saleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemStatus status;

    @Column(name = "image_url")
    private String imageUrl;

    @CreatedDate // ⭐ 변경 (JPA Auditing이 자동 주입)
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt; // ⭐ 타입 변경

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
