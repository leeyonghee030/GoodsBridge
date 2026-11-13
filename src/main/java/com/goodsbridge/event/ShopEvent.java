package com.goodsbridge.event;

import com.goodsbridge.item.Item;
import com.goodsbridge.shop.Shop;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
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

    // 1인당 최대 구매 수량 (가게가 이벤트 등록 때 정함, 1-10개)
    @Column(nullable = false, columnDefinition = "int default 1")
    private Integer maxPerUser;

    @Column(nullable = false)
    private LocalDateTime deadline;

    // 손님이 가게에 방문할 수 있는 마지막 날짜. 모집 마감 후에도 이 날까지는 번호표를 들고 방문하고,
    // 이 날이 지나야 '종료된 이벤트'가 된다. (예전 이벤트는 null → 마감일 + 7일로 본다)
    private LocalDate visitUntil;

    public static final int DEFAULT_VISIT_DAYS = 7;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShopEventStatus status;

    // 마감 후 가게 정산(SettlementHistory)까지 끝났는지. 스케줄러가 한 번만 정산하도록 표시
    @Column(nullable = false)
    private boolean settled;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder
    public ShopEvent(Shop shop, Item item, Integer capacity,LocalDateTime deadline, Integer feeAmount, Integer maxPerUser, LocalDate visitUntil) {
        this.shop = shop;
        this.item = item;
        this.capacity = capacity;
        this.currentCount = 0;
        this.feeAmount = feeAmount;
        this.maxPerUser = maxPerUser == null ? 1 : maxPerUser;
        this.deadline = deadline;
        this.visitUntil = visitUntil == null ? deadline.toLocalDate().plusDays(DEFAULT_VISIT_DAYS) : visitUntil;
        this.status = ShopEventStatus.OPEN;
    }

    // 이벤트 수정: 방문 날짜는 언제든, 나머지 조건은 서비스가 참가자가 없을 때만 부른다
    public void updateVisitUntil(LocalDate visitUntil) {
        this.visitUntil = visitUntil;
    }

    public void updateConditions(Integer capacity, Integer feeAmount, Integer maxPerUser, LocalDateTime deadline) {
        if (capacity != null) this.capacity = capacity;
        if (feeAmount != null) this.feeAmount = feeAmount;
        if (maxPerUser != null) this.maxPerUser = maxPerUser;
        if (deadline != null) this.deadline = deadline;
    }

    // 방문 마감일 (값이 없는 예전 이벤트는 마감일 + 7일)
    public LocalDate getVisitUntil() {
        return visitUntil != null ? visitUntil : deadline.toLocalDate().plusDays(DEFAULT_VISIT_DAYS);
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

    // currentCount/capacity 는 사람 수가 아니라 판매 수량(번호표 장수)
    public Integer increaseCurrentCount(int quantity) {
        return this.currentCount += quantity;
    }

    // 참가 취소로 수량이 반납되면 줄이고, 정원이 차서 마감됐던 이벤트는 다시 모집중으로 연다
    public void decreaseCurrentCount(int quantity) {
        this.currentCount -= quantity;
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
