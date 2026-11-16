-- 알림 테이블(notifications_tb)의 type, reference_type 칸을 enum 에서 문자열로 바꾼다.
-- (PR #82 이후 새 알림 종류가 저장되지 않는 문제 해결. docs/알림_DB_컬럼_수정_안내.md 참고)
--
-- 언제 실행해도 안전하다 (여러 번 실행해도 됨):
--   · 테이블이 아직 없으면  → 아무것도 바꾸지 않고 안내만 보여준다 (서버를 한 번 실행하면 테이블이 만들어진다)
--   · 이미 문자열이면       → 아무것도 바꾸지 않는다
--   · enum 이면            → 문자열로 바꾼다 (기존 알림 데이터는 그대로 유지)
--
-- 사용법 (프로젝트 DB 이름은 .env 의 DB_NAME, 기본값 goodsbridge):
--   docker exec -i goodsbridge-mysql mysql -uroot -p goodsbridge < docs/sql/fix_notifications_columns.sql
--   (Windows PowerShell 은 '<' 가 안 되니 아래 '직접 입력하는 방법'을 쓴다)
--
-- 직접 입력하는 방법:
--   docker exec -it goodsbridge-mysql mysql -uroot -p
--   mysql> USE goodsbridge;
--   mysql> (이 파일의 내용을 붙여 넣기)

SET @enum_cols = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications_tb'
      AND COLUMN_NAME IN ('type', 'reference_type')
      AND DATA_TYPE = 'enum'
);

SET @table_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications_tb'
);

SET @sql = CASE
    WHEN @table_exists = 0 THEN
        'SELECT ''notifications_tb 테이블이 아직 없어요. 서버를 한 번 실행한 뒤(테이블 자동 생성) 다시 실행해 주세요.'' AS info'
    WHEN @enum_cols = 0 THEN
        'SELECT ''이미 문자열 칸이라 바꿀 게 없어요.'' AS info'
    ELSE
        'ALTER TABLE notifications_tb MODIFY type VARCHAR(40) NOT NULL, MODIFY reference_type VARCHAR(40) NULL'
END;

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 결과 확인: type, reference_type 이 varchar(40) 이면 성공
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'notifications_tb'
  AND COLUMN_NAME IN ('type', 'reference_type');
