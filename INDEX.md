# INDEX

> AI는 질문을 받으면 이 파일부터 읽고, 아래에서 맞는 문서만 연다.

```yaml
docs:
- path: docs/guide/데모_임시데이터_사용법.md
  title: 데모 임시데이터 사용법·데모 계정과 결제 생략
  summary: APP_DEMO_ENABLED로 데모 모드를 켜고 데모 막대로 손님·가게·관리자 계정을 바꾸며, 결제 생략 방식과 들어 있는 시연 데이터, 초기화 시점과 주의할 점을 설명한다
  keywords:
  - 데모
  - 임시데이터
  - 시연 데이터
  - 데모 계정
  - 데모 막대
  - 결제 생략
  - 초기화
  - APP_DEMO_ENABLED
  - demo
- path: docs/guide/로컬_실행_가이드.md
  title: 로컬 실행 가이드·Windows PowerShell에서 처음 켜기
  summary: Docker로 MySQL을 띄우고 .env를 채워 Windows PowerShell에서 서버를 처음 켜는 순서와 테이블 확인, 자주 막히는 경우, 데모 모드 켜는 법을 설명한다
  keywords:
  - 로컬 실행
  - 처음 세팅
  - PowerShell
  - docker compose
  - MySQL
  - .env
  - bootRun
  - 데모 모드
  - local setup
- path: docs/guide/시연_순서.md
  title: 시연 순서·발표 때 보여줄 흐름과 기능 시연 화면 쓰는 법
  summary: 발표·시연 때 데모 계정 전환과 기능 시연 화면(동시성·노쇼 정산·시간 빨리감기)을 섞어 핵심 기능을 15~20분 안에 보여주는 순서와 준비, 말할 포인트, 오류가 날 때 대처를 정리한다
  keywords:
  - 시연 순서
  - 발표
  - 시연 대본
  - 기능 시연
  - 동시성 시연
  - 노쇼 정산
  - 시간 빨리감기
  - 데모
  - demo script
- path: docs/guide/오라클_클라우드_배포.md
  title: 오라클 클라우드 무료 서버에 배포하기·HTTPS 주소로 공개
  summary: Oracle Cloud Always Free 서버에 Docker Compose로 앱·MySQL·HTTPS 프록시(Caddy)를 올려 누구나 주소로 들어와 확인하게 하는 순서와, 카카오·PortOne·시연
    모드 설정, 업데이트와 자주 막히는 경우를 정리한다
  keywords:
  - 배포
  - 오라클 클라우드
  - Oracle Cloud
  - Always Free
  - 무료 서버
  - Docker
  - docker-compose.prod.yml
  - Caddy
  - HTTPS
  - nip.io
  - 도메인
  - 카카오 Redirect URI
  - deploy
- path: docs/ops/알림_DB_컬럼_수정_안내.md
  title: 알림 DB 컬럼 수정 안내·기존 로컬 DB SQL 조치
  summary: 'PR #82 이후 기존 로컬 DB에서 새 알림이 저장되지 않는 증상과 원인, docs/sql의 SQL로 알림·경매 enum 칸을 고치는 방법과 확인법을 설명한다'
  keywords:
  - 알림 저장 안 됨
  - DB 컬럼
  - enum
  - notifications_tb
  - SQL 조치
  - fix_notifications_columns
  - fix_enum_columns
  - 'PR #82'
- path: docs/ops/작업_분배.md
  title: 작업 분배·A/B/C/D 역할과 담당 이슈
  summary: 팀원 A·B·C·D가 맡은 이슈 번호와 만드는 것, 일정표상 작업 일차, 다른 역할과 부딪히는 지점을 정리한 작업 분배표
  keywords:
  - 작업 분배
  - 역할
  - 담당
  - 누가 뭘
  - 일정
  - 이슈 번호
  - 팀원
- path: docs/spec/AI_에이전트_학습노트.md
  title: AI 에이전트 학습 노트·POST /api/chat 동작
  summary: Spring AI와 Gemini로 만든 POST /api/chat이 어떻게 동작하는지 com.goodsbridge.chat 파일 지도, 핵심 개념, 직접 해 보기, 도구 추가 방법, 주의할 점으로 정리한
    학습 노트
  keywords:
  - AI 에이전트
  - 챗봇
  - Spring AI
  - Gemini
  - '@Tool'
  - function calling
  - /api/chat
  - chat
  - '이슈 #13'
- path: docs/spec/design/2026-11-03-exchange-propose-item-direct-design.md
  title: 교환 제안 플로우 재설계·ExchangeItem 대신 Item 직접 참조
  summary: 교환 제안(propose)에서 ExchangeRequest가 ExchangeItem 대신 Item을 직접 참조하도록 바꾼 결정과 ERD·엔티티·Repository·DTO·검증 변경, 문서·코드 영향
    범위를 정리한 설계 문서
  keywords:
  - 교환 제안
  - propose
  - ExchangeRequest
  - ExchangeItem
  - Item
  - 교환 재설계
  - '이슈 #10'
- path: docs/spec/design/2026-11-03-goodsbridge-system-architecture-design.md
  title: 시스템 아키텍처 설계 초안·모듈러 모놀리스
  summary: 모듈러 모놀리스 접근을 기준으로 시스템 컨텍스트, 애플리케이션 구조, 핵심 요청 흐름, 데이터 일관성, 배포·운영, 보안 기준, 성공 기준과 위험을 검토한 2026-11-03 설계 초안
  keywords:
  - 아키텍처
  - 설계
  - 모듈러 모놀리스
  - system architecture
  - 요청 흐름
  - 데이터 일관성
  - 보안
  - 배포
- path: docs/spec/기획서.md
  title: 기획서·비즈니스 모델·ERD·API 명세·유저플로우
  summary: 가게 거점 안전 교환 플랫폼의 개요와 B2B SaaS 수익 모델, Tier 1 핵심 기능, 테이블 구조와 ERD, 기술 스택, 14일 일정, 관리자 페이지, API 명세, 유저플로우, 기술명세를 정리한
    전체 기획 문서
  keywords:
  - 기획서
  - ERD
  - API 명세
  - 유저플로우
  - 비즈니스 모델
  - B2B SaaS
  - 보증금
  - 노쇼
  - 교환
  - 경매
  - 이벤트
  - planning
- path: docs/spec/이슈_전체정리.md
  title: 이슈 전체 로직 정리·이슈별 ERD·API·권한·검증
  summary: '이슈 #4부터 #34까지 기능별 작업 내용, ERD, API 엔드포인트, 유효성 검사, 권한 체크, 주의사항을 LLM에게 질문하기 좋게 한 문서에 모은 정리본'
  keywords:
  - 이슈
  - 이슈 정리
  - issue
  - ERD
  - API
  - 권한
  - 유효성 검사
  - 동시성
  - 정산
  - LLM 질문용
```
