package com.goodsbridge.event;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;

@Getter
@Entity
public class ShopEvent {

    @Id
    @GeneratedValue
    private Long id;
}
