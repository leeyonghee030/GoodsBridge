package com.goodsbridge.auction;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
public class Bid {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;
}
