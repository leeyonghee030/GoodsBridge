package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
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

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted = false; // 소프트 삭제 플래그

    @Version // 동시성 제어(낙관적 락)를 위한 버전 필드
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Long version;

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
        if (name == null || name.isBlank()) throw new BadRequestException("가게 이름은 필수입니다.");
        if (region == null || region.isBlank()) throw new BadRequestException("지역은 필수입니다.");
        if (address == null || address.isBlank()) throw new BadRequestException("주소는 필수입니다.");

        if (this.name.equals(name) && this.region.equals(region) && this.address.equals(address)) {
            return;
        }

        this.name = name;
        this.region = region;
        this.address = address;

        if (this.status == ShopStatus.APPROVED) {
            this.status = ShopStatus.PENDING; // 수정 시 재심사 대기
        }
    }

    public void updateStatus(ShopStatus newStatus) {
        if (this.status == newStatus) return;
        if (this.status == ShopStatus.APPROVED && newStatus == ShopStatus.PENDING) {
            throw new IllegalStateException("이미 승인 완료된 상점은 대기 상태로 되돌릴 수 없습니다.");
        }
        this.status = newStatus;
    }


    // 하드 딜리트 대신 소프트 삭제 처리
    public void deleteShop() {
        this.deleted = true;
        this.status = ShopStatus.CLOSED; // 폐점 상태로 전환
    }

    public boolean isOwner(Long userId) {
        return this.ownerUserId.equals(userId);
    }
}
