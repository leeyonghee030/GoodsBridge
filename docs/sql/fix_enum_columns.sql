-- enum 으로 만들어진 칸을 문자열(VARCHAR)로 바꾼다.
-- 예전 코드로 DB 를 만든 사람은 새로 추가된 enum 값(알림 종류, 결제 목적 AUCTION_BALANCE, 정산 종류 AUCTION_SALE 등)이
-- 저장되지 않는다. (spring.jpa.hibernate.ddl-auto: update 는 이미 있는 enum 칸에 값을 추가해 주지 않는다)
-- 증상: 서버 로그에  "Data truncated for column '...' at row 1"
--
-- 바꾸는 칸
--   notifications_tb.type            (알림 종류)
--   notifications_tb.reference_type  (알림이 가리키는 대상)
--   payment_tb.purpose               (결제 목적: 경매 잔금 AUCTION_BALANCE 등)
--   settlement_history_tb.type       (정산 종류: 경매 낙찰 AUCTION_SALE 등)
--
-- 언제 실행해도 안전하다 (여러 번 실행해도 됨):
--   · 테이블/칸이 없으면 건너뛴다 (서버를 한 번 실행하면 만들어진다)
--   · 이미 문자열이면 건너뛴다
--   · enum 이면 문자열로 바꾼다 (기존 데이터는 그대로 유지)
--
-- 사용법 (DB 이름은 .env 의 DB_NAME, 기본값 goodsbridge):
--   docker exec -it goodsbridge-mysql mysql -uroot -p
--   mysql> USE goodsbridge;
--   mysql> (이 파일의 내용을 붙여 넣기)

DROP PROCEDURE IF EXISTS gb_fix_enum;

DELIMITER //
CREATE PROCEDURE gb_fix_enum(IN tbl VARCHAR(64), IN col VARCHAR(64), IN is_nullable VARCHAR(3))
BEGIN
    DECLARE enum_cols INT DEFAULT 0;
    SELECT COUNT(*) INTO enum_cols
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col AND DATA_TYPE = 'enum';

    IF enum_cols > 0 THEN
        SET @gb_sql = CONCAT('ALTER TABLE ', tbl, ' MODIFY ', col, ' VARCHAR(40) ', IF(is_nullable = 'YES', 'NULL', 'NOT NULL'));
        PREPARE gb_stmt FROM @gb_sql;
        EXECUTE gb_stmt;
        DEALLOCATE PREPARE gb_stmt;
    END IF;
END//
DELIMITER ;

CALL gb_fix_enum('notifications_tb', 'type', 'NO');
CALL gb_fix_enum('notifications_tb', 'reference_type', 'YES');
CALL gb_fix_enum('payment_tb', 'purpose', 'NO');
CALL gb_fix_enum('settlement_history_tb', 'type', 'NO');

DROP PROCEDURE gb_fix_enum;

-- 결과 확인: 아래 칸이 모두 varchar(40) 이면 성공 (테이블이 아직 없으면 그 칸은 안 나온다)
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'notifications_tb' AND COLUMN_NAME IN ('type', 'reference_type'))
    OR (TABLE_NAME = 'payment_tb' AND COLUMN_NAME = 'purpose')
    OR (TABLE_NAME = 'settlement_history_tb' AND COLUMN_NAME = 'type'))
ORDER BY TABLE_NAME, COLUMN_NAME;
