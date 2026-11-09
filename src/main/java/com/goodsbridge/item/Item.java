package com.goodsbridge.item;

import com.goodsbridge._core.exception.BadRequestException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted = false; // 소프트 삭제 플래그

    @Version // 동시성 제어 (낙관적 락)
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Item(Long sellerUserId, Long shopId, Category category, String title,
                String description, Integer price, SaleType saleType, ItemStatus status, String imageUrl) {
        // 엔티티 생성 시점 방어 로직
        if (sellerUserId == null) {
            throw new BadRequestException("판매자 ID는 필수입니다.");
        }
        if (category == null) {
            throw new BadRequestException("카테고리는 필수입니다.");
        }
        if (title == null || title.isBlank()) {
            throw new BadRequestException("상품 제목은 필수입니다.");
        }
        if (price == null || price <= 0) {
            throw new BadRequestException("가격은 0보다 커야 합니다.");
        }

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
        // 수정 시점 방어 로직
        if (category == null) {
            throw new BadRequestException("카테고리는 필수입니다.");
        }
        if (title == null || title.isBlank()) {
            throw new BadRequestException("상품 제목은 필수입니다.");
        }
        if (price == null || price <= 0) {
            throw new BadRequestException("가격은 0보다 커야 합니다.");
        }

        this.category = category;
        this.title = title;
        this.description = description;
        this.price = price;
        if (saleType != null) {
            this.saleType = saleType;
        }
        if (status != null) {
            this.status = status;
        }
        this.imageUrl = imageUrl;
    }

    public void deleteItem() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.status = ItemStatus.CLOSED; // 삭제 시 판매 마감 처리
    }

    // 상품 복구 메서드 (삭제 전 상태로 원복)
    public void restore(ItemStatus previousStatus) {
        this.deleted = false;
        this.deletedAt = null;
        this.status = previousStatus != null ? previousStatus : ItemStatus.ON_SALE;
    }

    public boolean isOwner(Long userId) {
        return this.sellerUserId.equals(userId);
    }
}
