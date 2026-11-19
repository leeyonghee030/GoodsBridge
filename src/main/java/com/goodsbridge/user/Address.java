package com.goodsbridge.user;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
public class Address {
    private String postalCode;
    private String address;
    private String detailAddress;

    @Builder
    public Address(String postalCode, String address, String detailAddress) {
        this.postalCode = postalCode;
        this.address = address;
        this.detailAddress = detailAddress;
    }
}
