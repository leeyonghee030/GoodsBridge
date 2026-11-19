# GoodsBridge

굿즈(포토카드 등) 교환/거래 플랫폼. 오프라인 굿즈샵을 거점으로 안전 교환(보증금+노쇼 페널티)을 중개하는 중고거래 플랫폼입니다. 4인/14일 부트캠프 팀프로젝트.

전체 기획/ERD/API 명세/유저플로우는 [`docs/spec/기획서.md`](docs/spec/기획서.md) 참고.

## 기술 스택
- Java 21, Spring Boot, Gradle
- Spring Security + JWT (Access/Refresh)
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

# 2) 환경변수 파일 생성
cp .env.example .env
# .env 열어서 DB_PASSWORD 등 값 채우기 (로컬 DB라 아무 값이나 OK)

# 3) 로컬 MySQL 띄우기
docker compose up -d
docker ps   # goodsbridge-mysql 떠 있으면 성공

# 4) 서버 실행 (테이블은 Hibernate가 자동 생성)
./gradlew bootRun
```

MySQL 컨테이너는 한 번 띄워두면 계속 켜둬도 됩니다. 다음에 작업할 땐 `./gradlew bootRun`만 하면 됩니다.

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

## 협업 규칙
- **Git 전략**: `main`(배포/데모) / `develop`(기본, 작업 베이스) / `feature/{이슈번호}-{영문명}`. `main`·`develop` 직접 push 불가, PR + 리뷰 승인 1개 이상 필요, Squash merge만 허용
- **이슈 작성 규칙**: Issue 생성 시 자동으로 뜨는 템플릿 참고, 또는 [#22 이슈 컨벤션 가이드](../../issues/22) 참고
- **작업 보드**: [GoodsBridge 작업 보드](https://github.com/users/leeyonghee030/projects/7)
- **브랜치~PR~머지 절차**: [#14 가이드](../../issues/14) 참고
- **커밋**: Conventional Commits (`feat`/`fix`/`docs`/`refactor`/`test`/`chore`)

## 테스트 우선순위
교환 정산 금액 계산(노쇼 3케이스) > 이벤트/경매 동시성 > PortOne 결제 검증 — 이 세 가지는 자동화 테스트로 반드시 남깁니다. 나머지 단순 CRUD는 확인 후 테스트 코드를 지워도 됩니다.

## 환경/시크릿
- `.env`는 절대 커밋하지 않습니다 (`.gitignore`에 포함). `.env.example`만 커밋합니다.
- DB 비밀번호, JWT_SECRET, PortOne 키, Gemini API 키는 각자 `.env`에서 로컬 값으로 채웁니다.
