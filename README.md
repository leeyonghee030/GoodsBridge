# GoodsBridge

굿즈(포토카드 등) 교환/거래 플랫폼. 오프라인 굿즈샵을 거점으로 안전 교환(보증금+노쇼 페널티)을 중개하는 중고거래 플랫폼입니다. 4인/14일 부트캠프 팀프로젝트.

전체 기획/ERD/API 명세/유저플로우는 [`docs/spec/기획서.md`](docs/spec/기획서.md) 참고.

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

## 문서
- 시작하기: [로컬 실행 가이드](docs/guide/로컬_실행_가이드.md), [데모 임시데이터 사용법](docs/guide/데모_임시데이터_사용법.md)
- 기획·설계: [기획서](docs/spec/기획서.md), [이슈 전체 로직 정리](docs/spec/이슈_전체정리.md), [AI 에이전트 학습 노트](docs/spec/AI_에이전트_학습노트.md)
- 운영: [알림 DB 컬럼 수정 안내](docs/ops/알림_DB_컬럼_수정_안내.md), [작업 분배](docs/ops/작업_분배.md)
- 전체 문서 목록: [INDEX.md](INDEX.md)

## 팀 구성
| 역할 | GitHub | 맡은 영역 |
|---|---|---|
| A | [@life-random](https://github.com/life-random) | 공통 예외처리, Security·JWT, 카카오·이메일 로그인, 회원정보, 인앱 알림, AI 에이전트 |
| B | [@bito4165-creator](https://github.com/bito4165-creator) | 가게, 가게 구독(B2B), 상품·카테고리, 게시판·댓글 |
| C | [@leeyonghee030](https://github.com/leeyonghee030) | 교환·교환 보증금 정산, 교환 댓글, 가게 이벤트·선착순 참가 |
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
