package com.goodsbridge.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 알림 종류(Type/ReferenceType)에는 새 값이 계속 추가된다.
 * 컬럼이 enum 이거나 enum 값을 나열한 CHECK 제약이 붙으면, 이미 만들어진 DB 에서는 ddl-auto=update 가
 * 그 정의를 고쳐주지 않아 새 종류를 저장할 때 "Data truncated" / "Check constraint violated" 로 실패한다.
 * 그래서 알림 테이블에는 CHECK 제약이 없어야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationSchemaTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void notificationTable_hasNoCheckConstraint() {
        Integer checks = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.table_constraints "
                + "where upper(table_name) = 'NOTIFICATIONS_TB' and constraint_type = 'CHECK'",
            Integer.class);

        assertEquals(0, checks, "notifications_tb 에 CHECK 제약이 있으면 새 알림 종류를 저장하지 못할 수 있다");
    }
}
