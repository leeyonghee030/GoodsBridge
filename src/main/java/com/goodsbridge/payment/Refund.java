package com.goodsbridge.payment;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;

@Getter
@Entity
public class Refund {

    @Id
    @GeneratedValue
    private Long id;
}
