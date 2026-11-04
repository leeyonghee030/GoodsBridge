package com.goodsbridge.exchange;

import com.goodsbridge.item.Item;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "exchangeItem_tb")
public class ExchangeItem {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId",nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itemId",nullable = false)
    private Item item; // 내가 가진것

    @Column(nullable = false)
    private String wantDescription; //원하는거

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shopId", nullable = false)
    private Shop shop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExchangeItemStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder
    public ExchangeItem(User user, Item item, String wantDescription, Shop shop) {
        this.user = user;
        this.item = item;
        this.wantDescription = wantDescription;
        this.shop = shop;
        this.status = ExchangeItemStatus.OPEN;
    }
    // 편의 메서드
    // 교환 게시글 주인인지 확인
    public boolean isOwner(Long userId) {
        return this.user.getId().equals(userId);
    }

    // 교환 상태 모집중 인지 확인
    public boolean isOpen() {
        return this.status == ExchangeItemStatus.OPEN;
    }

    // 교환 상태 매칭완료 중인지 확인
    public boolean isMatched() {
        return this.status == ExchangeItemStatus.MATCHED;
    }

    //상태 변경 메서드
    public void updateStatus(ExchangeItemStatus status){
        this.status = status;
    }
}
