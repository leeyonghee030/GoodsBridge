package com.goodsbridge.event;

import com.goodsbridge.item.Item;
import com.goodsbridge.shop.Shop;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "shopEvent_tb")
@NoArgsConstructor
public class ShopEvent {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shopId", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itemId", nullable = false)
    private Item item;

    @Column(nullable = false)
    private Integer capacity;
    @Column(nullable = false)
    private Integer currentCount;

    @Column(nullable = false)
    private Integer feeAmount;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShopEventStatus status;

    // 마감 후 가게 정산(SettlementHistory)까지 끝났는지. 스케줄러가 한 번만 정산하도록 표시
    @Column(nullable = false)
    private boolean settled;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder
    public ShopEvent(Shop shop, Item item, Integer capacity,LocalDateTime deadline, Integer feeAmount) {
        this.shop = shop;
        this.item = item;
        this.capacity = capacity;
        this.currentCount = 0;
        this.feeAmount = feeAmount;
        this.deadline = deadline;
        this.status = ShopEventStatus.OPEN;
    }

    //편의메서드

    //이벤트연 가게가맞는가?
    public boolean isShop(Long userId){
        return this.shop.isOwner(userId);
    }

    //가게 item이 맞는가?
    public boolean isShopItem(Long userId) {
        return this.item.isOwner(userId);
    }

    //지금 모집중 status가맞는가?
    public boolean isOpen() {
        return this.status == ShopEventStatus.OPEN;
    }
    //status 업데이트
    public void updateStatus(ShopEventStatus shopEventStatus){
        this.status = shopEventStatus;
    }

    public Integer  increaseCurrentCount() {
        return ++this.currentCount;
    }

    // 참가 취소로 자리가 나면 인원을 줄이고, 정원이 차서 마감됐던 이벤트는 다시 모집중으로 연다
    public void decreaseCurrentCount() {
        this.currentCount--;
        if (this.status == ShopEventStatus.CLOSED) {
            this.status = ShopEventStatus.OPEN;
        }
    }

    public boolean isBeforeDeadline(LocalDateTime now) {
        return now.isBefore(this.deadline);
    }

    public void markSettled() {
        this.settled = true;
    }
}
