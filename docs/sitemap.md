| 영역 | 메서드 | 경로 | 기능 |
|---|---|---|---|
| 로그인 | GET | `/login` | 로그인 페이지 |
| 인증 | POST | `/login` | 로그인 시도 |
| 인증 | GET | `/kakao-login` | 카카오 로그인 시작 |
| 인증 | GET | `/oauth2/kakao?code={code}` | 카카오 콜백, 서비스 JWT 발급 및 쿠키 저장 |
| 인증 | POST | `/api/auth/reissue` | Refresh Token으로 토큰 재발급 |
| 인증 | POST | `/api/auth/logout` | 로그아웃 |
| 카테고리 | GET | `/api/categories` | 카테고리 목록 |
| 카테고리 | POST | `/api/categories` | 카테고리 생성 |
| 상품 | GET | `/api/items` | 상품 목록·검색 (`category`, `region`, `saleType`, 페이지 정보) |
| 상품 | GET | `/api/items/{id}` | 상품 상세 |
| 상품 | GET | `/api/items/me` | 내 상품 목록 |
| 상품 | POST | `/api/items` | 상품 등록 |
| 상품 | PATCH | `/api/items/{id}` | 상품 수정 |
| 상품 | DELETE | `/api/items/{id}` | 상품 삭제 |
| 가게 | GET | `/api/shops` | 가게 목록·검색 (`name`, `region`) |
| 가게 | GET | `/api/shops/{id}` | 가게 상세 |
| 가게 | GET | `/api/shops/me` | 내 가게 목록 |
| 가게 | POST | `/api/shops` | 입점 신청 |
| 가게 | PATCH | `/api/shops/{id}` | 가게 수정 |
| 가게 | DELETE | `/api/shops/{id}` | 가게 삭제 |
| 가게 관리 | GET | `/api/admin/shops` | 입점 관리 목록 (`status`) |
| 가게 관리 | PATCH | `/api/admin/shops/{id}/approve` | 입점 승인 |
| 가게 관리 | PATCH | `/api/admin/shops/{id}/reject` | 입점 거절 |
| 구매 | POST | `/api/items/{id}/purchase` | 상품 구매 |
| 구매 | GET | `/api/purchases/me` | 내 구매 내역 |
| 구매 | POST | `/api/purchases/{id}/confirm` | 상품 수령 확인 |
| 구매·배송 | GET | `/api/shops/{id}/purchases` | 가게의 발송 목록 (`status`) |
| 구매·배송 | PATCH | `/api/purchases/{id}/shipping` | 운송장·배송 상태 수정 |
| 결제 | GET | `/api/payments/me` | 내 결제 내역 |
| 환불 | GET | `/api/payments/me` | 내 결제 내역의 상품 환불 |
