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

    @Column(length = 100)
    private String title; // 게시글 제목 (상품 이름과 별개). 비어 있으면 상품 이름을 제목으로 보여준다

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
    public ExchangeItem(User user, Item item, String wantDescription, Shop shop, String title) {
        this.title = normalizeTitle(title);
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

    // 게시글 수정 (원하는 것, 교환 장소)
    public void update(String wantDescription, Shop shop) {
        this.wantDescription = wantDescription;
        this.shop = shop;
    }

    // 게시글 제목 수정 (상품 이름은 그대로). 비우면 상품 이름을 제목으로 보여준다
    public void changeTitle(String title) {
        this.title = normalizeTitle(title);
    }

    // 화면에 보여줄 게시글 제목: 직접 적은 제목, 없으면 상품 이름
    public String getDisplayTitle() {
        return title != null ? title : item.getTitle();
    }

    private static String normalizeTitle(String title) {
        return title == null || title.isBlank() ? null : title.trim();
    }

    //상태 변경 메서드
    public void updateStatus(ExchangeItemStatus status){
        this.status = status;
    }
}
