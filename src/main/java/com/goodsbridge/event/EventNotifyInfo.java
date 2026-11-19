package com.goodsbridge.event;

/**
 * 이벤트 참가·취소 알림에 쓰는 값(상품명, 가게 주인 id).
 * 참가 처리는 이벤트 행을 잠근 채 진행하므로, 알림 때문에 상품·가게를 잠금 안에서 따로 조회하지 않도록 잠금 전에 한 번에 읽는다.
 */
public record EventNotifyInfo(String itemTitle, Long ownerUserId) {
}
