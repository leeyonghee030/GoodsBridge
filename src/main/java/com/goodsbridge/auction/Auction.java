package com.goodsbridge.auction;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;

@Getter
@Entity
public class Auction {

    @Id
    @GeneratedValue
    private Long id;
}
