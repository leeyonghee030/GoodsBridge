# 알림 DB 컬럼 수정 안내 (PR #82 이후)

> 이미 로컬 DB를 만들어 둔 사람은 **SQL을 한 번** 실행해야 새 알림이 저장돼요.
> DB를 처음 만들었거나 새로 만든 사람은 필요 없어요.

## 증상
- 서버는 정상으로 뜨고, 구매·교환도 성공해요.
- 그런데 **새로 추가된 종류의 알림**(구매 완료, 배송, 교환 취소, 교환 예약 등)이 저장되지 않아요.
- 오류 화면은 안 나오고 서버 로그에만 이렇게 남아요.
  ```
  Data truncated for column 'reference_type' at row 1
  ```

## 원인
- 알림 테이블(`notifications_tb`)의 `type`, `reference_type` 칸이 **예전 값만 허용하는 MySQL enum** 으로 만들어져 있어요.
  ```
  reference_type  enum('AUCTION','EXCHANGE')
  type            enum('AUCTION_BID','AUCTION_WIN','EXCHANGE_APPROVED','EXCHANGE_REJECTED','EXCHANGE_REQUEST')
  ```
- PR #82에서 `DELIVERY`, `PURCHASE`, `EVENT`, `PAYMENT_COMPLETED`, `EXCHANGE_CANCELLED` 같은 새 값이 추가됐는데, `spring.jpa.hibernate.ddl-auto: update`는 **이미 있는 enum 칸에 값을 추가해 주지 않아요.**
- 그래서 DB를 새로 만들기 전에 이미 있던 칸에는 새 값이 들어가지 못해요.

## 해결 방법 (1분)
도커 MySQL 컨테이너가 떠 있는 상태에서 터미널에 입력해요.

```bash
docker exec -it goodsbridge-mysql mysql -uroot -p
```
비밀번호는 `.env`의 `DB_PASSWORD` 값이에요. 들어가면 이어서 입력해요. (`goodsbridge` 자리는 `.env`의 `DB_NAME` 값으로 바꿔요.)

```sql
USE goodsbridge;
ALTER TABLE notifications_tb MODIFY type VARCHAR(40) NOT NULL, MODIFY reference_type VARCHAR(40) NULL;
```
`Query OK`가 나오면 끝이에요. `exit`로 나오세요.

- **기존 알림 데이터는 그대로 남아요.**
- 서버 재시작은 필요 없어요.

### 더 안전한 방법: SQL 파일 실행
`docs/sql/fix_notifications_columns.sql` 은 **언제 실행해도 안전**해요. (여러 번 실행해도 됨)
- 테이블이 아직 없으면 → 아무것도 바꾸지 않고 "서버를 한 번 실행한 뒤 다시 실행하세요"라고 알려줘요.
- 이미 문자열 칸이면 → 바꿀 게 없다고 알려줘요.
- 예전 enum 칸이면 → 문자열로 바꿔요. (기존 알림은 그대로)

```bash
docker exec -i goodsbridge-mysql mysql -uroot -p goodsbridge < docs/sql/fix_notifications_columns.sql
```
(Windows PowerShell은 `<` 를 못 쓰니 위의 "해결 방법"처럼 mysql에 들어가서 파일 내용을 붙여 넣으세요.)

### 다른 방법: DB 새로 만들기
```bash
docker compose down -v
docker compose up -d
```
데이터가 모두 지워져요. 데모 데이터는 서버를 켜면 다시 만들어져요. (자세한 내용은 [데모 임시데이터 사용법](데모_임시데이터_사용법.md))

## 잘 됐는지 확인하는 법
1. 데모 막대에서 **B** 계정으로 바꿔요.
2. 상품(SHOP)을 하나 구매해요.
3. 화면 위쪽 종 모양 알림에 `[배송준비중]`이 뜨면 정상이에요.

## 근본 해결 (제안)
알림 담당 코드에서 `Notification`의 `type`, `reference_type`을 **enum 칸이 아니라 문자열 칸으로 만들어지게 지정**하면, 앞으로 알림 종류가 늘어도 이 문제가 생기지 않아요. 다만 **이미 만들어진 DB의 칸은 코드를 바꿔도 안 바뀌기 때문에**, 기존 DB는 위 SQL이 한 번은 필요해요.
