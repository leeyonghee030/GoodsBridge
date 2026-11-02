package com.goodsbridge.exchange;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;

@Getter
@Entity
public class ExchangeItem {

    @Id
    @GeneratedValue
    private Long id;
}
