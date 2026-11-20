package com.goodsbridge.notification;

import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB에 이미 저장된 알림의 옛 문구를 지금 코드가 만드는 문구와 같은 형식으로 한 번 고친다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationLegacyTextMigrationTest {

    @Autowired NotificationLegacyTextMigration migration;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;

    private static String fix(String type, String content) {
        return NotificationLegacyTextMigration.fixContent(type, content);
    }

    @Test
    void oldTextsBecomeExactlyWhatTheCurrentCodeWrites() {
        assertThat(fix("DELIVERY_STARTED", "'{포토카드}' 상품이 배송을 시작했습니다 ( 송장 : 123)"))
            .isEqualTo("'포토카드' 상품이 배송을 시작했습니다 ( 송장 : 123)");
        assertThat(fix("BOARD_REPLY", "{문의 제목}에 새로운 댓글이 달렸습니다")).isEqualTo("'문의 제목'에 새로운 댓글이 달렸습니다");
        assertThat(fix("BOARD_INQUIRY", "{재고 있나요} 문의가 등록되었습니다")).isEqualTo("'재고 있나요' 문의가 등록되었습니다");
        assertThat(fix("REFUND_REQUESTED", "환불 요청이 왔습니다 {15000}원")).isEqualTo("환불 요청이 왔습니다 (15,000원)");
        assertThat(fix("SHOP_APPLIED", "새 입점 신청이 있습니다: {데모 굿즈샵}")).isEqualTo("새 입점 신청이 있습니다: 데모 굿즈샵");
        assertThat(fix("AUCTION_WIN", " '{키링}' 경매가 취소되었습니다. 보증금은 모두 환불됩니다"))
            .isEqualTo("'키링' 경매가 취소되었습니다. 보증금은 모두 환불됩니다");
        assertThat(fix("AUCTION_WIN", "키링 경매가 30000원에 낙찰되었습니다")).isEqualTo("'키링' 경매가 30,000원에 낙찰되었습니다");
        assertThat(fix("AUCTION_WIN", "키링 경매가 유찰되었습니다")).isEqualTo("'키링' 경매가 유찰되었습니다");
        assertThat(fix("AUCTION_WIN", "키링 상품에 낙찰되셨습니다!\n2026-11-20T14:03:05.123까지 잔여 결제 금액 29000원을 결제해 주세요."))
            .isEqualTo("'키링' 상품에 낙찰되셨습니다!\n11월 20일 14:03까지 잔여 결제 금액 29,000원을 결제해 주세요.");
        assertThat(fix("AUCTION_PAID", "낙찰자가 키링 잔금을 결제했습니다. 배송을 준비해 주세요"))
            .isEqualTo("낙찰자가 '키링' 잔금을 결제했습니다. 배송을 준비해 주세요");
        assertThat(fix("EXCHANGE_MEETING_CONFIRMED", "12님과 34님의 약속이 2026-11-20T15:00에 약속이 확정되었습니다"))
            .isEqualTo("교환 약속이 11월 20일 15:00으로 확정되었습니다");
        assertThat(fix("EXCHANGE_MEETING_CONFIRMED", "2026-11-20T15:00에 약속이 확정되었습니다"))
            .isEqualTo("11월 20일 15:00으로 약속이 확정되었습니다");
        assertThat(fix("EXCHANGE_BOOKED", "2026-11-21T09:30:12.345까지 교환 예약 일정이 생겼습니다"))
            .isEqualTo("11월 21일 09:30까지 교환 예약 일정이 생겼습니다");
        assertThat(fix("EXCHANGE_REQUEST", "포토카드 세트에 교환 신청이 들어왔습니다")).isEqualTo("'포토카드 세트'에 교환 신청이 들어왔습니다");
        assertThat(fix("EXCHANGE_REJECTED", "키링 교환신청이 거절되었습니다. 보증금은 전액 환불됩니다"))
            .isEqualTo("'키링' 교환 신청이 거절되었습니다. 보증금은 전액 환불됩니다");
        assertThat(fix("EXCHANGE_SETTLED", "교환이 완료됐어요. 보증금 15000원이 환불돼요")).isEqualTo("교환이 완료됐어요. 보증금 15,000원이 환불돼요");
        assertThat(fix("EVENT_CLOSED", "'{굿즈}' 이벤트의 모집이 마감되었습니다. 번호표3로 가게에 방문해 주세요"))
            .isEqualTo("'굿즈' 이벤트의 모집이 마감되었습니다. 번호표 3번으로 가게에 방문해 주세요");
    }

    @Test
    void textsUsersWroteAreNotTouched() {
        String chat = "{내일} 2026-11-20T15:00에 10000원 들고 갈게요";
        assertThat(fix("EXCHANGE_MESSAGE", chat)).isEqualTo(chat);
        assertThat(fix("REFUND_REJECTED", " 재고가 2026-11-20T15:00에 들어와요")).isEqualTo(" 재고가 2026-11-20T15:00에 들어와요");
        // 게시글 제목 안의 날짜·금액은 바꾸지 않는다 (제목을 감싼 중괄호만 뗀다)
        assertThat(fix("BOARD_REPLY", "{2025-12-25T10:00 10000원 정산 문의}에 새로운 댓글이 달렸습니다"))
            .isEqualTo("'2025-12-25T10:00 10000원 정산 문의'에 새로운 댓글이 달렸습니다");
    }

    @Test
    void currentFormatTextsAreLeftAsTheyAre() {
        String[][] current = {
            {"AUCTION_WIN", "'{한정} 포카' 경매가 취소되었습니다. 보증금은 모두 환불됩니다"},
            {"AUCTION_WIN", "'2025-12-25 팬싸 포카' 경매가 30,000원에 낙찰되었습니다"},
            {"PURCHASE_ORDERED", "'포토카드' 주문이 완료됐어요. 가게에서 배송을 준비하고 있어요"},
            {"EXCHANGE_REQUEST", "'포토카드'에 교환 신청이 들어왔습니다"},
            {"EVENT_JOINED", "'굿즈' 이벤트에 참가했어요. 번호표 3번, 2026-11-24까지 가게에 방문해 받아 가세요"},
        };
        for (String[] c : current) {
            assertThat(fix(c[0], c[1])).as(c[1]).isEqualTo(c[1]);
        }
        assertThat(NotificationLegacyTextMigration.fixTitle("[교환신청]")).isEqualTo("[교환 신청]");
        assertThat(NotificationLegacyTextMigration.fixTitle("[교환 약속 승락됨]")).isEqualTo("[교환 약속 승낙됨]");
        assertThat(NotificationLegacyTextMigration.fixTitle("[새 주문]")).isEqualTo("[새 주문]");
    }

    @Test
    void runsOnlyOncePerDatabase() {
        User user = userRepository.save(User.builder().email("legacy@noti.test").password("pw")
            .nickname("legacy@noti.test").loginType(LoginType.EMAIL).build());
        String insert = "insert into notifications_tb (receiver_id, type, title, content, reference_type, reference_id, read_status, created_at)"
            + " values (?, 'REFUND_REQUESTED', '[환불거절]', '환불 요청이 왔습니다 {15000}원', 'REFUND', 1, 'UNREAD', current_timestamp)";
        jdbcTemplate.update("delete from data_migration_tb where name = ?", NotificationLegacyTextMigration.NAME);
        jdbcTemplate.update(insert, user.getId());

        migration.run(null);
        assertThat(jdbcTemplate.queryForList("select content from notifications_tb where receiver_id = ?", String.class, user.getId()))
            .containsExactly("환불 요청이 왔습니다 (15,000원)");

        // 한 번 실행한 DB 에서는 다시 돌지 않는다 (새로 생긴 알림을 건드리지 않음)
        jdbcTemplate.update(insert, user.getId());
        migration.run(null);
        assertThat(jdbcTemplate.queryForList("select content from notifications_tb where receiver_id = ? order by id", String.class, user.getId()))
            .containsExactly("환불 요청이 왔습니다 (15,000원)", "환불 요청이 왔습니다 {15000}원");
    }
}
