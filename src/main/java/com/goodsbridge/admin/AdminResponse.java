package com.goodsbridge.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminResponse {

    private long userCount;
    private long paymentCount;
    private long paymentAmount;
    private long revenue;
}
