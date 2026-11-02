package com.goodsbridge.shop;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;

@Getter
@Entity
public class Shop {

    @Id
    @GeneratedValue
    private Long id;
}
