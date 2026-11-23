---
id: spec-api
title: API 명세 (Tier 1)
category: spec
summary: Tier 1 REST API 엔드포인트 목록(메서드·경로·설명)과 결제 액션·AI 에이전트 호출 규칙. 기획서에서 분리한 참조 문서
keywords:
- API
- 엔드포인트
- REST
- 명세
- paymentId
- /api/chat
related_files: []
last_updated: "2026-11-23"
---

> 이 문서는 [기획서](기획서.md)에서 분리한 참조용 문서입니다. 화면 흐름은 [유저플로우](유저플로우.md), 테이블은 [ERD](ERD.md)를 보세요.

# API 명세 (Tier 1)

> 결제가 필요한 액션(이벤트참가/입찰/교환수락/구매)은 **클라이언트가 PG 결제창을 먼저 완료**하고 받은 `paymentId`를 요청 본문에 담아 보내면, 백엔드가 PortOne에 검증 요청 후 처리합니다 (기존 spring_blog 결제 패턴 재사용). 별도의 범용 "/charge" API는 만들지 않습니다.
>
> AI 에이전트는 **`POST /api/chat` 하나만 외부에 노출**됩니다. tool 함수들은 REST 엔드포인트가 아니라 서비스 계층을 직접 호출합니다.

## 인증 (카카오 소셜 로그인)
| Method | Path | 설명 |
|---|---|---|
| GET | /oauth2/authorization/kakao | 카카오 로그인 시작 (Spring Security 기본 제공, 카카오 동의 화면으로 리다이렉트) |
| GET | /login/oauth2/code/kakao | 카카오 콜백 (Spring Security가 처리) → 성공 시 User 자동 생성/조회 + 자체 JWT(Access/Refresh) 발급 |
| GET | /update | 내 정보 화면 (조회·수정 폼, 서버 렌더링). 2026-11-19: 실제 구현 경로로 정정 |
| GET | /api/users/me/default-address | 내 기본 주소 (구매·잔금 결제 배송지용, 없으면 204) |
| POST | /api/auth/logout | 로그아웃 — refreshToken 무효화 (2026-11-03 추가, 이슈 #32) |
| POST | /api/update | 회원정보 수정 (닉네임·비밀번호·프로필 사진·휴대폰·주소, multipart 폼). 주소를 적으면 상세주소 필수 (이슈 #32, 2026-11-19 실제 경로로 정정) |
| POST | /api/withdraw | 회원탈퇴 — 소프트 삭제 (이슈 #32, 2026-11-19 실제 경로로 정정) |

> 별도의 회원가입/로그인 API 없음. 카카오 로그인 성공 시 발급된 JWT를 그 이후 모든 요청의 `Authorization` 헤더에 사용.

## 가게(Shop)
| Method | Path | 설명 |
|---|---|---|
| POST | /api/shops | 입점 신청 (`name`, `region`, `address`, **`chainCode` 필수** — 틀리면 400) |
| GET | /api/shops?name=&region=&category= | 가게 디렉토리 조회 — `name`은 부분일치 이름검색 (2026-11-03 추가, 교환장소 배정용) |
| GET | /api/shops/{id} | 가게 상세 |
| PATCH | /api/admin/shops/{id}/approve | (관리자) 입점 승인 |
| PATCH | /api/admin/shops/{id}/reject | (관리자) 입점 거절. 이미 승인된 가게에 쓰면 **승인 취소**(구독 만료 + 판매중 상품 닫힘) |
| POST | /api/shops/{id}/subscription | 구독 시작/갱신 (`paymentId`, `days`, `amount` — 금액은 `days × 1,000원`, 승인된 가게 주인만, 결제 검증 후 즉시 활성화) |
| GET | /api/shops/{id}/subscription | 내 가게 구독 조회 (가게 주인) (2026-11-12 추가) |
| GET | /api/admin/subscriptions | (관리자) 전체 가게 구독 현황 목록 (2026-11-03 추가, 이슈 #16) |
| PATCH | /api/admin/subscriptions/{id}/cancel | (관리자) 구독 취소 — 판매중 상품만 닫음 (2026-11-12 추가) |
| GET | /api/shops/{id}/settlements | 가게 정산/매출 내역 조회 |

## 상품(Item) & 일반판매(안전결제)
| Method | Path | 설명 |
|---|---|---|
| GET | /api/categories | 카테고리 목록 (프론트 드롭다운용) |
| POST | /api/items | 상품 등록 |
| GET | /api/items?category=&region=&saleType= | 목록/검색 |
| GET | /api/items/{id} | 상세 |
| PATCH / DELETE | /api/items/{id} | 수정/삭제 |
| POST | /api/items/{id}/purchase | 구매 요청 (배송지 입력 + paymentId 검증 → 대금 HOLD) |
| POST | /api/purchases/{id}/confirm | 구매자 수령확인 → 판매자에게 RELEASE |
| GET | /api/purchases/me | 내 구매내역 |
| GET | /api/payments/me | 내 전체 결제내역 조회 (목적 불문, 2026-11-03 추가, 이슈 #9) |
| GET | /api/shops/{id}/purchases?status=UNSHIPPED | 가게 발송관리 목록 |
| PATCH | /api/purchases/{id}/shipping | 운송장번호 입력 / 배송상태 변경 |

## 교환(Exchange)
| Method | Path | 설명 |
|---|---|---|
| POST | /api/exchange-items | 교환 게시글 등록 (가진 것/원하는 것 + **shopId 필수** — 이름검색으로 교환장소 배정, 2026-11-03 변경) |
| GET | /api/exchange-items?category= | 탐색 |
| POST | /api/exchange-items/{id}/comments | 댓글 작성 (2026-11-03 추가, 이슈 #33) |
| GET | /api/exchange-items/{id}/comments | 댓글 목록 (2026-11-03 추가, 이슈 #33) |
| DELETE | /api/exchange-comments/{id} | 댓글 삭제 (2026-11-03 추가, 이슈 #33) |
| POST | /api/exchange-items/{id}/propose | 교환 제안 → `ExchangeRequest` PENDING 생성 (B는 **본인 소유 Item을 바로 골라서** 제안, 별도 게시글 불필요 — 2026-11-03 변경. shopId는 게시글의 shopId를 그대로 복사) |
| POST | /api/exchange-requests/{id}/accept | 상대방 수락 → 양측 보증금 결제 검증 → CONFIRMED |
| POST | /api/exchange-requests/{id}/reject | 상대방 거절 |
| POST | /api/exchange-requests/{id}/cancel | 취소 (LLM 쓰기 액션에서도 이 서비스 메서드 재사용) |
| PATCH | /api/shops/{shopId}/exchange-requests/{id}/check-in | 가게가 현장 출석여부 기록 → 완료/노쇼 정산 처리 |
| GET | /api/exchange-requests/me | 내 교환 현황 |

## 가게 이벤트(ShopEvent)
| Method | Path | 설명 |
|---|---|---|
| POST | /api/shops/{id}/events | 이벤트 생성 |
| GET | /api/events?shopId=&status= | 목록 |
| GET | /api/events/{id} | 상세 |
| PATCH | /api/events/{id} | (가게) 이벤트 수정 — 설명/방문 날짜는 항상, 수량·가격·참가비·마감은 참가자가 없고 모집 마감 전일 때만. 취소됐거나 방문 날짜가 오늘 이후가 아니면 불가 (2026-11-13 추가) |
| POST | /api/events/{id}/join | 참가신청/추가 구매 (★ 동시성 제어 지점, paymentId 검증 + 정원체크). 이미 참가했으면 같은 번호표에 구매 건을 추가하고, 내 수량 합계가 maxPerUser를 넘으면 거절 |
| DELETE | /api/events/{id}/join/{participationId} | 구매 건 단위 참가취소 (그 건의 참가비만 환불, 마감 전에만. 2026-11-13: 경로에 participationId 추가) |
| POST | /api/events/{id}/cancel | (가게) 이벤트 취소 → 전원 환불 |
| GET | /api/events/me | 내 참가내역 (구매 건 단위, 이벤트 정보·방문 날짜 포함) |
| GET | /api/shops/{shopId}/events/settlement | (가게) 이벤트별 참가비 합계/환불/정산 상태 (2026-11-13 추가) |
| GET | /api/shops/{shopId}/events/settlement-records | (가게) 정산된 이벤트 참가비 내역 — 오늘/이번 달/저번 달·달력 화면용 (2026-11-13 추가) |
| GET | /api/events/{id}/participations | (가게) 참가자 명단 (번호표·구매 내역) |

## 경매(Auction)
| Method | Path | 설명 |
|---|---|---|
| POST | /api/shops/{id}/auctions | 경매 개설 (body에 `depositAmount` 포함 — 가게가 직접 정하되 startPrice의 10%~20% 범위, 2026-11-03 추가) |
| GET | /api/auctions?status=&shopId= | 목록 (상품·가게 정보와 입찰 횟수 포함, `shopId`로 가게별 조회 — 2026-11-13 추가) |
| GET | /api/auctions/{id} | 상세 |
| POST | /api/auctions/{id}/bids | 입찰 (★ 동시성 제어 지점, 최초입찰 시 paymentId로 보증금 검증) |
| GET | /api/auctions/{id}/bids | 입찰내역 |
| GET | /api/auctions/me | 내가 참여한 경매 (페이지는 0부터) |
| POST | /api/auctions/{id}/pay-balance | 낙찰자 잔금 결제 (낙찰가 − 보증금, 배송지 필요) |
| PATCH | /api/auctions/{id}/cancel · close | 경매 취소(입찰 없을 때 가게 / 관리자) · 마감 |

## 게시판
| Method | Path | 설명 |
|---|---|---|
| POST/GET | /api/boards | 작성/목록 |
| GET | /api/boards/{id} | 상세 |
| POST | /api/boards/{id}/replies | 댓글 작성 |
| DELETE | /api/replies/{id} | 댓글 삭제 |

## 결제/환불/관리자
| Method | Path | 설명 |
|---|---|---|
| POST | /api/refunds | 환불 요청 |
| PATCH | /api/admin/refunds/{id}/approve | (관리자) 환불 승인 → 부분/전체 취소 실행 |
| PATCH | /api/admin/refunds/{id}/reject | (관리자) 환불 거절 |
| GET | /api/admin/shops/search?status=PENDING | (관리자) 입점 대기/승인/거절 목록 (`name`, `region`, `status` 필터) |
| GET | /api/admin/dashboard | (관리자) 가입자수/거래량/매출 통계 |

## AI 에이전트
| Method | Path | 설명 |
|---|---|---|
| POST | /api/chat | 메시지 전송 → Spring AI `ChatClient` 호출 (tool calling 포함) |
| GET | /api/chat/history | 대화 로그 조회 |

## 알림 (2026-11-03 추가, 이슈 #34)
| Method | Path | 설명 |
|---|---|---|
| GET | /api/notifications/me | 내 알림 목록 (읽은 것 포함, 최신순, `?page=&size=`) |
| GET | /api/notifications/unread | 안 읽은 알림 목록 (헤더 알림 패널) |
| GET | /api/notifications/unread/count | 안 읽은 알림 개수 |
| PATCH | /api/notifications/{id}/read | 알림 읽음 처리 |
| PATCH | /api/notifications/read-all | 전체 읽음 처리 |

