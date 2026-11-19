package com.goodsbridge.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 구매·낙찰 잔금 결제 화면의 배송지에 미리 채울 "내 기본 주소" (내 정보에 저장한 주소).
 * 화면 조각 fragments/delivery-address 가 모델의 defaultAddress 로 쓴다. 주소가 없으면 null.
 */
@Component
@RequiredArgsConstructor
public class DefaultAddressProvider {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Map<String, String> find(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
            .map(User::getAddress)
            .filter(address -> address != null && address.getAddress() != null && !address.getAddress().isBlank())
            .map(address -> Map.of(
                "postalCode", nullToEmpty(address.getPostalCode()),
                "address", address.getAddress(),
                "detailAddress", nullToEmpty(address.getDetailAddress())))
            .orElse(null);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
