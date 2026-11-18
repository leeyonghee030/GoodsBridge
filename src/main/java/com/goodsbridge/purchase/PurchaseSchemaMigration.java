package com.goodsbridge.purchase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 환불 후 재판매를 막던 옛 DB 제약을 정리한다.
 * 예전에는 purchase_tb 에 item_id 유니크(uk_purchase_item)가 있었고 전액 환불 시 구매 행을 삭제했다.
 * 지금은 환불된 구매 행을 남기고 active_item_id 유니크(uk_purchase_active_item)로 중복 판매를 막는다.
 * ddl-auto=update 는 옛 제약을 지우지 않으므로, 기존 DB 에서는 환불한 상품을 다시 구매할 때
 * 옛 제약에 걸려 "이미 판매된 상품입니다." 가 나온다. 서버가 켜질 때 한 번 정리한다 (여러 번 실행해도 안전).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseSchemaMigration implements ApplicationRunner {

    private static final String TABLE = "purchase_tb";
    private static final String LEGACY_UNIQUE = "uk_purchase_item";
    private static final String ITEM_INDEX = "idx_purchase_item_id";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // MySQL 전용 정리 (테스트용 H2 는 create-drop 으로 새로 만들어지므로 대상이 아니다)
            if (!isMySql()) {
                return;
            }
            dropLegacyItemUnique();
            syncActiveItemId();
        } catch (Exception e) {
            // 정리에 실패해도 서버는 뜨도록 한다
            log.warn("구매 테이블 제약 정리에 실패했습니다. 환불 후 재구매가 막히면 DB 를 직접 확인해주세요.", e);
        }
    }

    // 옛 item_id 유니크 제거 (FK 가 item_id 인덱스를 필요로 하므로 일반 인덱스를 먼저 만든다)
    private void dropLegacyItemUnique() {
        if (indexCount(LEGACY_UNIQUE) == 0) {
            return;
        }
        if (indexCount(ITEM_INDEX) == 0) {
            jdbcTemplate.execute("create index " + ITEM_INDEX + " on " + TABLE + " (item_id)");
        }
        jdbcTemplate.execute("alter table " + TABLE + " drop index " + LEGACY_UNIQUE);
        log.info("purchase_tb 옛 유니크 제약({})을 제거했습니다.", LEGACY_UNIQUE);
    }

    // active_item_id 는 환불되지 않은 구매만 값을 가진다 (환불 구매는 NULL 이어야 같은 상품을 다시 팔 수 있다)
    private void syncActiveItemId() {
        int filled = jdbcTemplate.update(
            "update " + TABLE + " set active_item_id = item_id where status <> 'REFUNDED' and active_item_id is null");
        int cleared = jdbcTemplate.update(
            "update " + TABLE + " set active_item_id = null where status = 'REFUNDED' and active_item_id is not null");
        if (filled > 0 || cleared > 0) {
            log.info("purchase_tb active_item_id 보정 - 채움={}건, 비움={}건", filled, cleared);
        }
    }

    private int indexCount(String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
            select count(*) from information_schema.statistics
            where table_schema = database() and table_name = ? and index_name = ?
            """, Integer.class, TABLE, indexName);
        return count == null ? 0 : count;
    }

    private boolean isMySql() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase().contains("mysql");
        }
    }
}
