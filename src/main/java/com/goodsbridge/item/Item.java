package com.goodsbridge.item;

import com.goodsbridge._core.exception.BadRequestException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

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

    @Column(nullable = false, length = 100)
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
    private boolean deleted = false;

    @Version
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "paused_by_subscription", nullable = false)
    private boolean pausedBySubscription = false;

    @Builder
    public Item(
        Long sellerUserId,
        Long shopId,
        Category category,
        String title,
        String description,
        Integer price,
        SaleType saleType,
        ItemStatus status,
        String imageUrl
    ) {
        validateItem(shopId, category, title, description, price);

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

    // 상품 정보 수정
    public void update(
        Long shopId,
        Category category,
        String title,
        String description,
        Integer price,
        SaleType saleType,
        ItemStatus status,
        String imageUrl
    ) {
        validateItem(shopId, category, title, description, price);

        // 다른 가게로 이동하면 기존 구독 중지 이력 초기화
        if (!Objects.equals(this.shopId, shopId)) {
            this.pausedBySubscription = false;
        }

        this.shopId = shopId;
        this.category = category;
        this.title = title;
        this.description = description;
        this.price = price;

        if (saleType != null) {
            this.saleType = saleType;
        }

        // 점주가 상태를 명시적으로 변경한 경우
        if (status != null) {
            this.status = status;
            this.pausedBySubscription = false;
        }

        this.imageUrl = imageUrl;
    }

    // 상품 입력값 검증
    private void validateItem(
        Long shopId,
        Category category,
        String title,
        String description,
        Integer price
    ) {
        if (shopId == null || shopId <= 0) {
            throw new BadRequestException("가게 ID는 필수입니다.");
        }

        if (category == null) {
            throw new BadRequestException("카테고리는 필수입니다.");
        }

        if (title == null || title.isBlank()) {
            throw new BadRequestException("상품 제목은 필수입니다.");
        }

        if (title.length() > 100) {
            throw new BadRequestException("상품 제목은 100자 이내여야 합니다.");
        }

        if (description != null && description.length() > 5000) {
            throw new BadRequestException("상품 설명은 5000자 이내여야 합니다.");
        }

        if (price == null || price <= 0) {
            throw new BadRequestException("가격은 0보다 커야 합니다.");
        }

        if (price > 100_000_000) {
            throw new BadRequestException("상품 가격은 1억 원을 초과할 수 없습니다.");
        }
    }

    // 실제 상품 소프트 삭제
    public void deleteItem() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.status = ItemStatus.CLOSED;
        this.pausedBySubscription = false;
    }

    // 실제 삭제된 상품 복구
    public void restore(ItemStatus previousStatus) {
        this.deleted = false;
        this.deletedAt = null;
        this.status = previousStatus != null
            ? previousStatus
            : ItemStatus.ON_SALE;
        this.pausedBySubscription = false;
    }

    // 점주가 직접 상품 상태 변경
    public void updateStatus(ItemStatus status) {
        if (status == null) {
            throw new BadRequestException("상품 상태는 필수입니다.");
        }

        this.status = status;
        this.pausedBySubscription = false;
    }

    public boolean isOwner(Long userId) {
        return this.sellerUserId.equals(userId);
    }
}
