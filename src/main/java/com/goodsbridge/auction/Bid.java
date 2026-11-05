package com.goodsbridge.auction;

import com.goodsbridge.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "bid_tb", indexes = {
    @Index(name = "idx_bid_auction_user", columnList = "auction_id, user_id")
})
@NoArgsConstructor
public class Bid {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer bidAmount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Bid(Auction auction, User user, Integer bidAmount) {
        this.auction = auction;
        this.user = user;
        this.bidAmount = bidAmount;
    }
}
