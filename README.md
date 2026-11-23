# GoodsBridge

굿즈(포토카드 등) 교환/거래 플랫폼. 오프라인 굿즈샵을 거점으로 안전 교환(보증금+노쇼 페널티)을 중개하는 중고거래 플랫폼입니다. 4인/14일 팀프로젝트.

기획·설계는 `docs/spec/`에 문서별로 나뉘어 있습니다: [기획서](docs/spec/기획서.md)(개요·비즈니스 모델·핵심 기능·일정), [ERD](docs/spec/ERD.md), [API 명세](docs/spec/API_명세.md), [유저플로우](docs/spec/유저플로우.md).

## 시연 주소 (임시)
**https://poking-stand-gainfully.ngrok-free.dev**

- 시연(데모) 모드로 켜 두어 가입·결제 없이 데모 계정(손님 A·B, 가게, 관리자)으로 바로 둘러볼 수 있습니다.
- 개발자 PC에서 실행 중일 때만 접속됩니다. 서버(오라클 클라우드) 배포가 끝나면 주소가 바뀝니다.
- 처음 접속하면 ngrok 안내 화면이 한 번 나올 수 있습니다. **Visit Site**를 누르면 됩니다.

## 핵심 기능
- **가게 중개 안전 교환**: 교환이 확정되면 양쪽이 보증금을 내고, 가게가 현장 출석을 확인한 뒤 노쇼 여부에 따라 보증금을 정산합니다.
- **상품 판매·구매**: 카드 결제(PortOne), 배송 단계 관리, 구매 확정, 부분 환불
- **선착순 이벤트**: 정원·1인 수량 제한, 비관적 락으로 동시 참가 제어, 번호표 발급
- **경매**: 최초 입찰 시 보증금, 비관적 락으로 동시 입찰 제어, 낙찰 후 잔금 결제·미납 처리
- **가게 B2B 구독**: 수익 모델은 유저 수수료가 아니라 가게 대상 구독료입니다.
- **문의·리뷰·고객센터와 실시간 알림**: 상품·이벤트·경매 문의, 구매 확정 후 리뷰, 고객센터 문의, SSE 알림
- **AI 챗봇**: Spring AI + Gemini, `POST /api/chat` 하나로 서비스 안내와 내 거래 조회
- **관리자 페이지**: 입점 승인, 구독·경매 관리, 고객센터 문의 답변

## 기술 스택
- Java 21, Spring Boot, Gradle
- Spring Security + JWT (Access/Refresh) — 카카오 로그인, 이메일 회원가입·로그인(인증 메일)
- Spring Data JPA + MySQL
- Spring AI (`spring-ai-starter-model-google-genai`, Gemini) — `@Tool` function calling
- PortOne 카드결제
- Docker Compose (로컬 MySQL)
- Mustache (서버사이드 뷰 — 핵심 유저 화면 + 관리자 페이지만, 나머지는 REST API만)

## 빠른 시작

**사전 준비** (컴퓨터당 한 번만)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) 설치 후 실행
- Java 21, Git

```bash
# 1) 클론
git clone https://github.com/leeyonghee030/GoodsBridge.git
cd GoodsBridge
git checkout develop   # 기본 브랜치라 보통 자동

# 2) 환경변수 파일 생성 (예시 값 그대로도 서버는 켜집니다)
cp .env.example .env

# 3) 로컬 MySQL 띄우기
docker compose up -d
docker ps   # goodsbridge-mysql 떠 있으면 성공

# 4) 서버 실행 (테이블은 Hibernate가 자동 생성) → http://localhost:8080
./gradlew bootRun
```

MySQL 컨테이너는 한 번 띄워두면 계속 켜둬도 됩니다. 다음에 작업할 땐 `./gradlew bootRun`만 하면 됩니다.
Windows PowerShell 기준 자세한 순서와 자주 막히는 경우는 [로컬 실행 가이드](docs/guide/로컬_실행_가이드.md)를 보세요.

**`.env` 값**
- 서버를 켜는 데 필요한 값: `DB_*`, `JWT_*`, `APP_DEMO_ENABLED`(`true` 또는 `false`, 빈 값이면 서버가 켜지지 않음). `.env.example`에 예시 값이 들어 있습니다.
- 기능을 쓸 때만 필요한 값: 결제 `PORTONE_*`, AI 챗봇 `GEMINI_API_KEY`, 카카오 로그인 `KAKAO_*`, 회원가입 인증 메일 `MAIL_*`. 예시 값(`changeme`)으로도 서버는 켜지고, 해당 기능만 동작하지 않습니다.

**시연(데모) 모드**: `.env`에서 `APP_DEMO_ENABLED=true`로 켜면 가입·결제 없이 데모 계정(손님 A·B, 가게, 관리자)으로 바로 둘러볼 수 있습니다. 데모 데이터는 서버를 켤 때마다 다시 만들어집니다. 자세한 내용은 [데모 임시데이터 사용법](docs/guide/데모_임시데이터_사용법.md).

**테스트 실행**
```bash
./gradlew test   # H2 메모리 DB를 써서 MySQL 없이 돌아갑니다
```

## 화면 (Mustache)
화면은 Mustache로 서버에서 그립니다. 경로는 다음과 같습니다.
- 둘러보기: `/test/item`, `/test/event`, `/test/auction`, `/test/shop`, `/test/board`, `/test/chat` (상세는 `/test/{도메인}/{id}`)
- 마이페이지: `/my/purchases`, `/my/exchange`, `/my/events`, `/my/auctions`, `/my/inquiries`
- 가게 관리: `/shop/dashboard`, `/shop/items`, `/shop/exchange`, `/shop/events`, `/shop/auctions`, `/shop/inquiries`, `/shop/reviews`
- 관리자: `/test/admin` (입점·구독·경매·고객센터 문의)
- 기능 시연: `/demo/concurrency`, `/demo/auction`, `/demo/settlement`, `/demo/timeline` (데모 모드에서만)

## 기능 시연
손으로 재현하기 어려운 기능(동시 요청, 노쇼 정산, 마감 스케줄러)을 버튼 한 번으로 보여주는 화면입니다.
- **켜는 법**: `.env`에서 `APP_DEMO_ENABLED=true`로 두고 서버를 켭니다.
- **여는 법**: 오른쪽 위 **DEMO** 메뉴에서 **기능 시연**을 누르고, 화면 위쪽 탭(선착순 이벤트 / 경매 입찰 / 노쇼 정산 / 시간 빨리감기)으로 오갑니다. 로그인하지 않아도 됩니다.
- **임시 데이터**: 모든 시연은 누를 때마다 임시 가게·사용자·상품을 만들고, 끝나면 모두 지웁니다. 다른 데모 데이터와 섞이지 않고 몇 번이든 다시 누를 수 있습니다.
- **결제**: 데모 결제번호(`demo-pay-`)를 써서 PortOne 없이 통과합니다.
- **서버를 막 켰을 때**: 서버가 켜지고 몇 초 안에 누르면 시작 작업(데모 데이터 생성·구독 정합성 검사)과 겹쳐 오류가 날 수 있습니다. 잠시 뒤 다시 누르세요.

### 1. 선착순 이벤트 동시 참가 (`/demo/concurrency`)
선착순 이벤트에 여러 명이 같은 순간에 참가 신청할 때 정원을 넘겨 팔리지 않는지를 보여줍니다. 락이 없을 때와 있을 때를 나란히 비교합니다.

**사용법**: 정원(1~20)과 동시 신청 인원(1~50)을 넣고 **동시에 신청 보내기**를 누릅니다. 기본값은 정원 5명, 20명 동시 신청입니다. 결과 카드 두 개를 비교합니다.

| | 락 없음 | 락 있음 (실제 서비스 코드) |
|---|---|---|
| 성공 인원 | 정원보다 많음 (초과 판매) | 정원과 같음 |
| DB에 기록된 판매 수 | 성공 인원보다 적음 (서로 덮어씀) | 정원과 같음 |
| 이벤트 상태 | 다를 수 있음 | `CLOSED` |

숫자는 실행할 때마다 조금씩 달라집니다. 락 있음 쪽은 항상 정원만큼만 성공합니다.

**설명할 때 포인트**
- 참가자가 모두 준비되면 신청을 한꺼번에 보냅니다.
- **락 없음**: 여러 명이 같은 "현재 판매 수"를 동시에 읽고 모두 "자리 있음"으로 통과합니다. 그래서 정원보다 많이 성공하고, 저장할 때 서로 값을 덮어써서(lost update) DB의 판매 수는 실제 성공 수보다 작게 남습니다. 충돌이 잘 보이도록 정원 확인과 수량 증가 사이에 결제 확인 시간을 흉내 낸 50ms 대기를 넣었습니다. 시연 전용 코드라 실제 서비스에서는 쓰지 않습니다.
  - 예: 50명 동시 신청이면 DB 판매 수는 5로 남습니다. 스레드는 50개가 동시에 출발하지만 DB 연결 풀(HikariCP 기본값)이 10개라, 10명씩 같은 값을 읽고 똑같이 +1 해서 저장합니다. 그래서 10명이 성공해도 판매 수는 1만 오릅니다. 판매 수가 정원보다 작으니 이벤트도 마감되지 않고 "모집 중"으로 남습니다. 이 설명은 결과 카드 안에도 나옵니다.
- **락 있음**: 실제 참가 신청 코드 `EventService.saveEventParticipation`을 그대로 부릅니다. `ShopEventRepository.findByIdForUpdate`가 이벤트 행을 `@Lock(PESSIMISTIC_WRITE)`, 즉 `SELECT ... FOR UPDATE`로 잠급니다. 다음 사람은 앞 사람의 처리가 끝날 때까지 기다린 뒤 최신 판매 수를 읽으므로 정원을 넘지 않습니다.

### 2. 경매 동시 입찰 (`/demo/auction`)
입찰이 없는 경매(시작가 10,000원, 보증금 1,000원)에 여러 명이 같은 순간, 같은 금액(시작가)으로 첫 입찰을 보냅니다. 정상이라면 1명만 최고 입찰자가 되고, 나머지는 "현재가보다 높아야 한다"로 거절돼야 합니다.

| | 락 없음 | 락 있음 (실제 서비스 코드) |
|---|---|---|
| "최고 입찰자" 응답 | 여러 명 (DB 연결 수인 약 10명) | 1명 |
| 저장된 입찰·걷힌 보증금 | 응답 받은 사람 수만큼 | 1건 |

- **락 없음**: 첫 입찰이 저장되기 전에 들어온 사람들이 모두 "아직 입찰 없음"을 읽고 통과합니다. 그래서 여러 명이 최고 입찰자라는 응답을 받고 보증금도 냈지만, 경매의 최고 입찰자 칸에는 마지막에 저장한 1명만 남습니다.
- **락 있음**: 실제 입찰 코드 `BidService.placeBid`를 그대로 부릅니다. `AuctionRepository.findByIdLock`이 경매 행을 잠가서, 두 번째 사람부터는 바뀐 현재가를 보고 거절됩니다.

### 3. 교환 노쇼 정산 (`/demo/settlement`)
A·B의 상품 가격을 넣고 누르면 "둘 다 옴 / 한쪽만 노쇼 / 둘 다 노쇼" 3가지 경우의 보증금, 환불액, 가게 몫을 한 번에 보여줍니다. 각 경우가 기획서 3.3절 정산 규칙과 일치하는지도 표시합니다.
- 경우마다 실제 교환 흐름 그대로 진행합니다. B 제안(B 보증금) → A 수락(A 보증금) → 가게 출석 체크 → 방문 기한을 지난 것으로 당김 → 기한 스케줄러 `ExchangeService.autoCloseExchangeEvent`가 판정·환불·가게 정산.
- 화면의 금액은 결제 기록의 환불액과 가게 정산 기록에서 읽은 실제 값입니다.
- 예: A 30,000원, B 8,000원이면 보증금은 A 6,000원, B 2,000원입니다. B는 20%가 1,600원이라 최소 금액 2,000원이 적용됩니다. 둘 다 오면 A 4,500원·B 1,500원을 돌려받고 가게 몫은 2,000원입니다. B만 안 오면 A는 전액 환불, B 보증금 2,000원은 가게 몫입니다. 둘 다 안 오면 A 3,000원·B 1,000원을 돌려받고 가게 몫은 4,000원입니다.

### 4. 시간 빨리감기 (`/demo/timeline`)
마감 시간이 지나야 도는 스케줄러를 기다리지 않고 바로 보여줍니다. 위쪽에서 시나리오 6개(**경매 · 낙찰 성공**, **경매 · 잔금 미납**, **선착순 이벤트**, **가게 구독 만료**, **교환 · 제안 기한 만료**, **교환 · 출석 체크 유예**) 중 하나를 고르고 실행하면 아래 단계를 차례로 실행합니다. 단계마다 "상태"와 "돈 흐름(환불·정산)", 새 알림을 타임라인으로 보여주고, 앞 단계와 달라진 값은 "이전 → 지금"으로 표시합니다.
- **경매 · 낙찰 성공**: A 10,000원 → B 12,000원 → C 15,000원 입찰 → 마감 시간 지남(C 낙찰, A·B 보증금 환불) → C가 배송지를 적고 잔금 14,000원 결제(구매 내역 생성) → 가게가 송장 입력해 발송 → 배송 완료 → C 구매 확정(낙찰가 15,000원 가게 정산). 잔금 결제·발송·구매 확정은 실제 서비스 코드(`AuctionService.payBalance`, `PurchaseService.shipping`, `PurchaseService.confirm`)를 사용자·가게 대신 부릅니다.
- **경매 · 잔금 미납**: 입찰 3건 → 마감 시간 지남(C 낙찰, A·B 보증금 환불, 잔금 기한 설정) → 잔금 기한 지남(낙찰 취소, C 보증금 1,000원은 가게 몫)
- **선착순 이벤트**: 정원 5명에 3명 참가 → 마감 시간 지남(마감, 참가비 3,000원을 가게 정산에 기록, 참가자·가게 알림)
- **가게 구독 만료**: 구독 중인 가게(일반 상품 2개 + 이벤트 상품 1개로 선착순 이벤트 진행) → 만료 3일 전(만료 임박 알림, 원래 매일 09:00) → 만료일 지남(구독 만료, 가게 상품 3개 모두 판매 중지, 원래 매일 00:00) → 손님이 이벤트 참가 시도(거절). 실제 스케줄러 `ShopSubscriptionBatchService`를 바로 실행합니다.
- **교환 · 제안 기한 만료**: B가 보증금 2,000원을 내고 교환 제안 → A가 응답하지 않은 채 응답 기한(3일) 지남 → 제안 만료, B 보증금 전액 환불
- **교환 · 출석 체크 유예**: 교환 확정 → 가게가 두 사람 출석 체크(5분 유예 시작, 바로 스케줄러를 돌려도 정산 안 됨) → 실수로 B 체크 해제(되돌리기) → 다시 체크 → 5분 지남(교환 완료, 각자 보증금 75% 환불·25% 가게). 실제 스케줄러 `ExchangeService.completeAfterGrace`(30초마다)를 바로 실행합니다.
- 스케줄러는 그 순간 기한이 지난 다른 데이터도 함께 처리합니다. 원래 곧 스케줄러가 처리할 데이터라 결과는 같습니다.
- 마감 시간(경매 마감·잔금 기한·이벤트 마감은 1분 전, 구독 만료일은 3일 뒤/어제, 교환 기한은 하루 전, 출석 체크 유예는 6분 전)을 지난 것으로 당긴 뒤, 원래 정해진 주기에 도는 실제 스케줄러(`AuctionScheduler.autoCloseAuctions`, `EventService.autoCloseShopEvents`, `ShopSubscriptionBatchService`, `ExchangeService.autoCloseExchangeEvent`·`completeAfterGrace`)를 바로 한 번 실행합니다.

### 관련 코드와 테스트
- 시연: `demo/DemoConcurrencyService.java`(이벤트·경매 동시성), `demo/DemoSettlementService.java`(노쇼 정산), `demo/DemoTimelineService.java`(빨리감기), `demo/DemoTempData.java`(임시 데이터), `templates/demo/`
- 실제 락: `event/ShopEventRepository.findByIdForUpdate`, `auction/AuctionRepository.findByIdLock`
- 자동화 테스트: `EventParticipationConcurrencyTest`, `BidConcurrencyTest`, `BidConnectionPoolTest`, `DemoConcurrencyServiceTest`, `DemoScenarioServiceTest`
  ```bash
  ./gradlew test --tests "*ConcurrencyTest" --tests "*ConnectionPoolTest" --tests "com.goodsbridge.demo.*"
  ```

## 문서
- 시작하기: [로컬 실행 가이드](docs/guide/로컬_실행_가이드.md), [데모 임시데이터 사용법](docs/guide/데모_임시데이터_사용법.md), [시연 순서](docs/guide/시연_순서.md)
- 배포: [오라클 클라우드 배포](docs/guide/오라클_클라우드_배포.md)
- 기획·설계: [기획서](docs/spec/기획서.md), [ERD](docs/spec/ERD.md), [API 명세](docs/spec/API_명세.md), [유저플로우](docs/spec/유저플로우.md), [이슈 전체 로직 정리](docs/spec/이슈_전체정리.md), [AI 에이전트 학습 노트](docs/spec/AI_에이전트_학습노트.md)
- 운영: [알림 DB 컬럼 수정 안내](docs/ops/알림_DB_컬럼_수정_안내.md), [작업 분배](docs/ops/작업_분배.md)
- 전체 문서 목록: [INDEX.md](INDEX.md)

## 팀 구성
| 역할 | GitHub | 맡은 영역 |
|---|---|---|
| A | [@life-random](https://github.com/life-random) | 공통 예외처리, Security·JWT, 카카오·이메일 로그인, 회원정보, 인앱 알림, AI 에이전트 |
| B | [@bito4165-creator](https://github.com/bito4165-creator) | 가게, 가게 구독(B2B), 상품·카테고리, 게시판·댓글 |
| C (팀장) | [@leeyonghee030](https://github.com/leeyonghee030) | 교환·교환 보증금 정산, 교환 댓글, 가게 이벤트·선착순 참가<br>기획서·README·이슈/PR 템플릿 작성과 이슈 생성·작업 분배, Mustache 화면 교체(#24), AI 에이전트 채팅 추가(#97), 기능 시연 화면, 서버 배포(오라클 클라우드·임시 주소) |
| D | [@DevDash-boot](https://github.com/DevDash-boot) | 결제·구매(PortOne), 정산·환불, 경매·입찰, 관리자 대시보드 |

자세한 분배와 이슈 번호는 [작업 분배](docs/ops/작업_분배.md)를 보세요.

## 협업 규칙
- **Git 전략**: `main`(배포/데모) / `develop`(기본, 작업 베이스) / `feature/{이슈번호}-{영문명}`. `main`·`develop` 직접 push 불가, PR + 리뷰 승인 1개 이상 필요, Squash merge만 허용
- **이슈 작성 규칙**: Issue 생성 시 자동으로 뜨는 템플릿 참고, 또는 [#22 이슈 컨벤션 가이드](https://github.com/leeyonghee030/GoodsBridge/issues/22) 참고
- **작업 보드**: [GoodsBridge 작업 보드](https://github.com/users/leeyonghee030/projects/7)
- **브랜치~PR~머지 절차**: [#14 가이드](https://github.com/leeyonghee030/GoodsBridge/issues/14) 참고
- **커밋**: Conventional Commits (`feat`/`fix`/`docs`/`refactor`/`test`/`chore`)

## 테스트 우선순위
교환 정산 금액 계산(노쇼 3케이스) > 이벤트/경매 동시성 > PortOne 결제 검증 — 이 세 가지는 자동화 테스트로 반드시 남깁니다. 나머지 단순 CRUD는 확인 후 테스트 코드를 지워도 됩니다.

## 환경/시크릿
- `.env`는 절대 커밋하지 않습니다 (`.gitignore`에 포함). `.env.example`만 커밋합니다.
- DB 비밀번호, JWT_SECRET, PortOne 키, Gemini API 키, 카카오 키, 메일 앱 비밀번호는 각자 `.env`에서 로컬 값으로 채웁니다.
