package com.goodsbridge.item;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;

@Getter
@Entity
public class Item {

    @Id
    @GeneratedValue
    private Long id;
}
