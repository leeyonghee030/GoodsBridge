# GoodsBridge

굿즈(포토카드 등) 교환/거래 플랫폼. 오프라인 굿즈샵을 거점으로 안전 교환(보증금+노쇼 페널티)을 중개하는 중고거래 플랫폼입니다. 4인/14일 부트캠프 팀프로젝트.

전체 기획/ERD/API 명세/유저플로우는 [`docs/기획서.md`](docs/기획서.md) 참고.

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
각 도메인 API가 아직 완성 전이라, 화면은 전부 `/test/{도메인}` 경로의 placeholder 상태입니다 (예: `/test/item`, `/test/admin`). 백엔드 완성 순서대로 실제 화면으로 교체됩니다 — 자세한 건 [#24 화면 전환 체크리스트](../../issues/24) 참고. 담당자가 직접 만들 필요는 없습니다.

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
