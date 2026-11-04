# 교환 제안(propose) 플로우 재설계 — ExchangeItem 대신 Item 직접 참조

- 이슈: #10 (ExchangeItem/ExchangeRequest CRUD + 매칭)
- 담당: owner-C (exchange/event 도메인)
- 날짜: 2026-11-03

## 배경 / 문제

기존 설계(#10 최초 스펙)는 교환 제안(B)이 A의 게시글에 제안하려면, 자기 명의로 `ExchangeItem`(교환 게시글: 가진 것 + 원하는 것 + 교환장소)을 **먼저 등록해둔 상태**여야 했다. `ExchangeRequest.exchangeItemB`가 `ExchangeItem`을 FK로 참조하는 구조였기 때문이다.

담당자가 제기한 문제 2가지:
1. **UX 마찰** — 다른 사람 게시글을 보다가 "이 아이템으로 교환하고 싶다"는 생각이 들어도, 별도로 "게시글 작성" 화면을 거쳐야만 제안이 가능해서 번거로움.
2. **Item이 가게 카탈로그인 줄 오해** — 실제로는 `Item`이 유저가 `POST /api/items`로 직접 등록하는 "내 상품"(title/description/price/imageUrl 보유, #7 스펙)이라 이 문제는 애초에 해당하지 않는 것으로 확인됨.

문제 2는 오해였고 소거되었지만, 문제 1(불필요한 사전 게시글 작성 단계)은 실제 설계 결함으로 확인됨: B가 제안할 때 필요한 건 "내가 가진 Item 하나"뿐인데, 굳이 `wantDescription`/`shopId`를 채운 독립 게시글을 만들게 강제하고 있었다. `shopId`는 어차피 propose 시 A의 게시글 것을 그대로 복사하므로 B 쪽 게시글에 적는 값이 의미가 없고, `wantDescription`도 이 특정 제안과 무관한 B의 "평소 희망사항"이라 어색하다.

## 결정된 방향

`ExchangeRequest`가 B 측을 `ExchangeItem`이 아니라 **`Item`을 직접 참조**하도록 변경한다. B는 자기가 이미 등록해둔 Item(`GET /api/items/me`로 선택) + 짧은 제안 메시지만으로 바로 propose할 수 있다.

## 1. ERD 변경 — `ExchangeRequest`

| 컬럼 | 기존 | 변경 |
|---|---|---|
| exchangeItemBId → **itemBId** | FK → ExchangeItem | FK → **Item** (B가 제안에 쓰는 본인 소유 상품) |
| **message** (신규) | 없음 | String, nullable — B가 A에게 보내는 짧은 제안 메시지 |
| exchangeItemAId, shopId, depositAmountA/B, userAShowed/BShowed, deadline | 변경 없음 | 변경 없음 |

`ExchangeItem`(A의 공개 게시글) 자체의 ERD는 변경 없음.

## 2. 엔티티 변경 — `ExchangeRequest.java`

- `exchangeItemB`(ExchangeItem 타입) 필드 → `itemB`(Item 타입)로 교체
- `isProposer(Long userId)`: `this.itemB.getSellerUserId().equals(userId)`로 변경 (Item의 `sellerUserId`로 B를 판별하므로 별도 user 참조 불필요)
- `message` 필드(String, nullable) + getter 추가
- `isOwner(Long userId)`(A 판별)는 변경 없음 — 계속 `exchangeItemA.getUser().getId()` 사용

## 3. Repository 변경

- `ExchangeRequestRepository.existsByExchangeItemBAndStatusIn(Long exchangeItemId, List<ExchangeRequestStatus>)` → `existsByItemBAndStatusIn(Long itemId, List<ExchangeRequestStatus>)`로 변경, JPQL도 `e.itemB.id = :itemId`로 교체.
- `findActiveRequestsByItem(ExchangeItem item)`(경쟁 제안 자동취소용, 이슈 #18 소관): 기존엔 `e.exchangeItemA = :item or e.exchangeItemB = :item` 두 분기였으나, `itemB`는 더 이상 `ExchangeItem`이 아니므로 이 분기가 성립하지 않는다. propose 시점에 이미 "같은 Item 중복 제안 금지"를 검증하므로, accept 시 자동취소 대상은 **같은 A 게시글(`exchangeItemA`)에 걸린 다른 PENDING 제안들**만 찾으면 충분하다. 쿼리를 `e.status = 'PENDING' and e.exchangeItemA = :item`으로 단순화.
  - 이 변경은 이슈 #10이 아니라 #18(정산) 코드에 있으므로, #18 담당자에게 변경사항 공유 필요.

## 4. DTO 변경

```java
// ExchangeRequestCreateRequest
Long itemBId;   // was exchangeItemBId — B가 제안에 쓸 본인 소유 Item id
String message; // 신규, nullable — A에게 보내는 짧은 제안 메시지

// ExchangeRequestResponse
Long itemBId;   // was exchangeItemBId
String message; // 신규
```

## 5. 검증/권한 (Service 구현 시 적용)

- `itemB`는 로그인 유저 소유(`Item.sellerUserId == userId`)가 아니면 403 (`ForbiddenException`)
- `itemB`가 이미 다른 PENDING/CONFIRMED 요청에 쓰이고 있으면 400 (`BadRequestException`) — `existsByItemBAndStatusIn`으로 체크
- 자기 자신의 게시글(`exchangeItemA.user == 로그인유저`)엔 제안 불가 — 400

## 6. 상태 추적 단순화 (부수 효과)

기존엔 accept 시 `ExchangeItemA`와 `ExchangeItemB` 둘 다 `MATCHED`로 전환해야 했다(이슈 #18). B가 더 이상 `ExchangeItem`을 갖지 않으므로, B 쪽 상태 전환 자체가 필요 없어진다. "이 Item이 이미 다른 제안에 묶여 있는가"는 `ExchangeRequest`에서 `itemB.id` 기준으로 PENDING/CONFIRMED 조회만 하면 되므로, `Item` 엔티티에 교환용 상태값을 새로 추가할 필요가 없다 (Item 담당자와 조율할 범위가 줄어듦).

## 7. 문서 영향 범위

- `기획서.md`: `ExchangeRequest` ERD 표, 교환 플로우 다이어그램("유저B: 상대 게시글에 교환 제안" 노드 설명), API 표(`propose` 설명)
- `이슈_전체정리.md`: 이슈 #10의 ERD/유효성검사/권한 섹션, 이슈 #18에 "경쟁 제안 자동취소 쿼리 단순화" 메모 추가

## 8. 코드 영향 범위

- `ExchangeRequest.java` (필드 교체, 편의메서드 수정)
- `ExchangeRequestRepository.java` (쿼리 2건 수정)
- `dto/ExchangeRequestCreateRequest.java`, `dto/ExchangeRequestResponse.java` (이미 생성된 파일, 재작성 필요)
- `ExchangeViewController.java`의 `propose` 핸들러 (아직 스켈레톤, 이 설계 기준으로 구현)
- `ExchangeService`에 `save()`/`propose()` 등 실제 로직 구현 시 위 검증 규칙 반영

## 범위 밖

- `ExchangeItem`(A의 공개 게시글) 자체의 구조는 변경하지 않는다 — 여전히 Item + wantDescription + shopId로 구성된 독립 게시글.
- `ExchangeComment`(#33, A의 게시글에 다는 댓글)는 영향 없음 — 댓글은 계속 `ExchangeItem`(A의 게시글)에만 달린다.
