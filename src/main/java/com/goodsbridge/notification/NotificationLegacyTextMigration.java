package com.goodsbridge.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 이미 DB에 저장된 알림의 옛 문구를 지금 문구 형식으로 한 번 고친다.
 * 예전 알림은 상품명·제목을 '{...}' 로 감싸 중괄호가 그대로 보였고, 날짜는 "2026-11-20T15:00:00.123",
 * 금액은 "15000원" 처럼 저장됐다.
 *
 * - DB마다 한 번만 실행한다 (data_migration_tb 에 실행 기록을 남긴다). 새 형식 알림을 다시 건드리지 않게 하기 위함이다.
 * - 예전 코드가 실제로 만들던 문장 모양만 정확히 골라 바꾼다.
 * - 사용자가 직접 쓴 글이 그대로 들어가는 알림(교환 채팅 메시지, 환불 거절 사유)은 제목만 고치고 내용은 건드리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationLegacyTextMigration implements ApplicationRunner {

    static final String NAME = "notification-legacy-text-v1";

    // 띄어쓰기·맞춤법을 맞춘 제목 (예전 제목 → 지금 제목). 제목은 항상 시스템이 만든 값이라 모든 알림에 적용한다
    static final Map<String, String> TITLES = Map.ofEntries(
        Map.entry("[교환거절]", "[교환 거절]"),
        Map.entry("[교환승인]", "[교환 신청 승인]"),
        Map.entry("[교환신청]", "[교환 신청]"),
        Map.entry("[교환예약]", "[교환 예약]"),
        Map.entry("[교환취소]", "[교환 취소]"),
        Map.entry("[환불거절]", "[환불 거절]"),
        Map.entry("[배송완료]", "[배송 완료]"),
        Map.entry("[배송시작]", "[배송중]"),
        Map.entry("[배송준비중]", "[배송 준비중]"),
        Map.entry("[교환 약속 승락됨]", "[교환 약속 승낙됨]")
    );

    // 내용에 사용자가 쓴 글이 그대로 들어가는 알림 (내용은 그대로 둔다)
    private static final Set<String> USER_TEXT_TYPES = Set.of("EXCHANGE_MESSAGE", "REFUND_REJECTED");
    // 예전 코드가 날짜(LocalDateTime 원형)·쉼표 없는 금액을 넣던 알림
    private static final Set<String> DATE_TYPES = Set.of("AUCTION_WIN", "EXCHANGE_MEETING_CONFIRMED", "EXCHANGE_BOOKED");
    private static final Set<String> AMOUNT_TYPES = Set.of("AUCTION_WIN", "PURCHASE_CANCELLED_BY_SHOP", "EVENT_CANCELLED", "EXCHANGE_SETTLED");

    // 예전 문장 모양
    private static final Pattern QUOTED_BRACES = Pattern.compile("'\\{([^']*?)\\}'");                     // '{상품명}'
    private static final Pattern BOARD_TITLE = Pattern.compile("^\\{(.*?)\\}(?=에 | 문의가 | 리뷰가 )");   // {글 제목}에 ...
    private static final Pattern REFUND_REQUESTED = Pattern.compile("^환불 요청이 왔습니다 \\{(\\d+)\\}원$");
    private static final Pattern SHOP_APPLIED = Pattern.compile("^새 입점 신청이 있습니다: \\{(.*)\\}$");
    private static final Pattern MEETING_WITH_IDS = Pattern.compile("^\\d+님과 \\d+님의 약속이 (.+)에 약속이 확정되었습니다$");
    private static final Pattern MEETING_ACCEPTED = Pattern.compile("^(.+)에 약속이 확정되었습니다$");
    private static final Pattern AUCTION_ENDED = Pattern.compile("^(?!')(.+) 경매가 (\\d+원에 낙찰되었습니다|유찰되었습니다)$");
    private static final Pattern AUCTION_WON = Pattern.compile("^(?!')(.+) 상품에 낙찰되셨습니다!");
    private static final Pattern AUCTION_PAID = Pattern.compile("^낙찰자가 (?!')(.+) 잔금을 결제했습니다");
    private static final Pattern EXCHANGE_REQUESTED = Pattern.compile("^(?!')(.+)에 교환 신청이 들어왔습니다$");
    private static final Pattern EXCHANGE_REJECTED_OR_CANCELLED =
        Pattern.compile("^(?!')(.+) 교환신청이 (거절되었습니다|취소되었습니다)");
    private static final Pattern ISO_DATE_TIME =
        Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2})(?::\\d{2}(?:\\.\\d+)?)?");
    private static final Pattern AMOUNT_WITHOUT_COMMA = Pattern.compile("(?<![\\d,.])(\\d{4,})원");
    private static final Pattern TICKET = Pattern.compile("번호표(\\d+)로 ");

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureMigrationTable();
            Integer done = jdbcTemplate.queryForObject(
                "select count(*) from data_migration_tb where name = ?", Integer.class, NAME);
            if (done != null && done > 0) {
                return;
            }
            int fixed = migrateRows();
            jdbcTemplate.update("insert into data_migration_tb (name, applied_at) values (?, current_timestamp)", NAME);
            log.info("저장된 알림 {}건의 옛 문구를 지금 형식으로 고쳤습니다.", fixed);
        } catch (Exception e) {
            // 정리에 실패해도 서버는 뜨도록 한다 (알림 문구 모양만의 문제). 실행 기록을 남기지 않으므로 다음 시작 때 다시 시도한다
            log.warn("저장된 알림 문구 정리에 실패했습니다.", e);
        }
    }

    // 실행 기록 테이블은 없을 때만 만든다 (테이블 변경 문장은 진행 중인 트랜잭션을 확정해 버리므로 매번 실행하지 않는다)
    private void ensureMigrationTable() {
        try {
            jdbcTemplate.queryForObject("select count(*) from data_migration_tb", Integer.class);
        } catch (org.springframework.dao.DataAccessException missing) {
            jdbcTemplate.execute("create table if not exists data_migration_tb ("
                + "name varchar(100) not null primary key, applied_at timestamp not null)");
        }
    }

    int migrateRows() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select id, type, title, content from notifications_tb");
        int fixed = 0;
        for (Map<String, Object> row : rows) {
            String type = (String) row.get("type");
            String title = (String) row.get("title");
            String content = (String) row.get("content");
            String newTitle = fixTitle(title);
            String newContent = fixContent(type, content);
            if (!newTitle.equals(title) || !newContent.equals(content)) {
                jdbcTemplate.update("update notifications_tb set title = ?, content = ? where id = ?",
                    newTitle, newContent, ((Number) row.get("id")).longValue());
                fixed++;
            }
        }
        return fixed;
    }

    static String fixTitle(String title) {
        return title == null ? null : TITLES.getOrDefault(title, title);
    }

    static String fixContent(String type, String content) {
        if (content == null || USER_TEXT_TYPES.contains(type)) {
            return content;
        }
        String s = content.startsWith(" '") ? content.substring(1) : content;

        Matcher m = MEETING_WITH_IDS.matcher(s);
        if (m.matches()) {
            s = "교환 약속이 " + m.group(1) + "으로 확정되었습니다";
        } else if ("EXCHANGE_MEETING_CONFIRMED".equals(type) && (m = MEETING_ACCEPTED.matcher(s)).matches()) {
            s = m.group(1) + "으로 약속이 확정되었습니다";
        }
        if ((m = REFUND_REQUESTED.matcher(s)).matches()) {
            s = "환불 요청이 왔습니다 (" + NotificationText.won(Long.parseLong(m.group(1))) + ")";
        }
        if ((m = SHOP_APPLIED.matcher(s)).matches()) {
            s = "새 입점 신청이 있습니다: " + m.group(1);
        }
        s = replace(QUOTED_BRACES, s, r -> "'" + r.group(1) + "'");
        s = replace(BOARD_TITLE, s, r -> "'" + r.group(1) + "'");
        s = replace(AUCTION_ENDED, s, r -> "'" + r.group(1) + "' 경매가 " + r.group(2));
        s = replace(AUCTION_WON, s, r -> "'" + r.group(1) + "' 상품에 낙찰되셨습니다!");
        s = replace(AUCTION_PAID, s, r -> "낙찰자가 '" + r.group(1) + "' 잔금을 결제했습니다");
        s = replace(EXCHANGE_REQUESTED, s, r -> "'" + r.group(1) + "'에 교환 신청이 들어왔습니다");
        s = replace(EXCHANGE_REJECTED_OR_CANCELLED, s, r -> "'" + r.group(1) + "' 교환 신청이 " + r.group(2));
        if (DATE_TYPES.contains(type)) {
            s = replace(ISO_DATE_TIME, s, r -> NotificationText.when(LocalDateTime.of(
                Integer.parseInt(r.group(1)), Integer.parseInt(r.group(2)), Integer.parseInt(r.group(3)),
                Integer.parseInt(r.group(4)), Integer.parseInt(r.group(5)))));
        }
        if (AMOUNT_TYPES.contains(type)) {
            s = replace(AMOUNT_WITHOUT_COMMA, s, r -> NotificationText.won(Long.parseLong(r.group(1))));
        }
        if ("EVENT_CLOSED".equals(type)) {
            s = TICKET.matcher(s).replaceAll("번호표 $1번으로 ");
        }
        return s;
    }

    private static String replace(Pattern pattern, String s, Function<MatchResult, String> to) {
        return pattern.matcher(s).replaceAll(r -> Matcher.quoteReplacement(to.apply(r)));
    }
}
