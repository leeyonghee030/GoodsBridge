package com.goodsbridge.shop;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop_tb")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerUserId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShopStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Shop(Long ownerUserId, String name, String region, String address, ShopStatus status) {
        this.ownerUserId = ownerUserId;
        this.name = name;
        this.region = region;
        this.address = address;
        this.status = status != null ? status : ShopStatus.PENDING; // 기본값 PENDING 처리
    }

    public void update(String name, String region, String address) {
        this.name = name;
        this.region = region;
        this.address = address;
    }

    public void updateStatus(ShopStatus status) {
        this.status = status;
    }

    public boolean isOwner(Long userId) {
        return this.ownerUserId.equals(userId);
    }
}
