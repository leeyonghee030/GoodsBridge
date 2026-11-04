package com.goodsbridge.auction;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BidRepository extends JpaRepository<Bid, Long> {
    boolean existsByAuction(Auction auction);
}
