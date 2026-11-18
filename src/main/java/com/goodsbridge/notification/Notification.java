package com.goodsbridge.notification;

import com.goodsbridge.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications_tb")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    public Notification(
        User receiver,
        Type type,
        String title,
        String content,
        ReferenceType referenceType,
        Long referenceId
    ) {
        this.receiver = receiver;
        this.type = type;
        this.title = title;
        this.content = content;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 알림을 받는 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    // 알림 카테고리
    // enum 컬럼으로 만들면 종류가 늘 때마다 DB 의 enum 정의를 바꿔야 하므로 varchar 로 저장한다
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 50)
    private Type type;

    // 알림 제목
    @Column(nullable = false, length = 100)
    private String title;

    // 알림 내용
    @Column(nullable = false, length = 500)
    private String content;

    // 관련 데이터 유형
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 50)
    private ReferenceType referenceType;

    // 관련 데이터 ID
    private Long referenceId;

    // 읽음 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReadStatus readStatus = ReadStatus.UNREAD;

    // 생성 시간
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // 읽음 처리
    public void markAsRead() {
        this.readStatus = ReadStatus.READ;
    }

    public void updateContent(String title, String content) {
        this.title = title;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public enum ReadStatus {
        READ, UNREAD;
    }

    public enum Type {

        EXCHANGE_REQUEST,       // 교환 신청
        EXCHANGE_APPROVED,      // 교환 승인
        EXCHANGE_REJECTED,      // 교환 거절
        EXCHANGE_CANCELLED,     // 교환 취소
        EXCHANGE_BOOKED,        // 교환 예약
        EXCHANGE_EXPIRED,       // 교환 만료
        EXCHANGE_SETTLED,       // 교환 종료
        EXCHANGE_COMMENT,       // 댓글 작성

        COMMENT_DELETED_BY_ADMIN,   // 운영자 권한 내 댓글 삭제

        EXCHANGE_MEETING_PROPOSED,  // 교환 약속 신청
        EXCHANGE_MEETING_CONFIRMED, // 교환 약속 확정
        EXCHANGE_VISIT_REMINDER,    // 방문 기한 확인 메시지

        EXCHANGE_MESSAGE,       // 교환 대상 메시지

        EVENT_CANCELLED,        // 이벤트 취소
        EVENT_CLOSED,           // 이벤트 인원 마감

        AUCTION_BID,            // 경매 입찰
        AUCTION_WIN,            // 경매 낙찰
        AUCTION_LOST,           // 경매 패찰
        AUCTION_OUTBID,         // 상위 입찰 당함
        AUCTION_ENDING_SOON,    // 경매 마감 임박
        AUCTION_PAID,           // 낙찰자 잔금 결제
        AUCTION_FORFEITED,      // 잔금 미납으로 낙찰 무효

        PAYMENT_COMPLETED,      // 결제 완료 (배송 준비중)
        DELIVERY_STARTED,       // 배송 시작 (배송중)
        DELIVERY_COMPLETED,     // 배송 완료

        SHOP_APPLIED,           // 가게 입점 신청
        SHOP_REJECTED,          // 가게 입점 거절
        SHOP_APPROVAL_CANCELED, // 가게 입점 승인 취소
        SHOP_CLOSED,            // 가게 폐점

        AUCTION_REMAINING_PAYMENT,  // 경매 잔금 납부 요청
        REFUND_REJECTED,            // 환불 거절
        REFUND_APPROVED,            // 환불 승인
        REFUND_REQUESTED,           // 환불 신청

        SUBSCRIPTION_CANCELLED,     // 구독 취소
        SUBSCRIPTION_EXPIRED,       // 구독 만료
        SUBSCRIPTION_EXPIRING,      // 구독 만료 임박
        SUBSCRIPTION_PAID,          // 신규 구독

        PURCHASE_ORDERED,           // 새 주문
        PURCHASE_CANCELLED_BY_SHOP, // 가게 사유 환불처리

        BOARD_REPLY                 // 계시글 댓글
    }

    public enum ReferenceType {
        EXCHANGE("/my/exchange?request={id}"),      // 내 교환 내역에서 그 신청의 팝업을 바로 연다
        SHOP_EXCHANGE("/shop/exchange?request={id}"), // 가게 사장님의 교환 관리 (그 신청의 팝업을 연다)
        EXCHANGE_POST("/exchange/{id}"),            // 교환 게시글
        PURCHASE("/my/purchases"),                  // 구매 내역
        REFUND("/my/purchases"),                    // 환불 관련 구매 내역
        EVENT("/test/event/{id}"),                  // 이벤트 상세
        AUCTION("/test/auction/{id}"),             // 경매 상세 (낙찰 알림의 잔금 결제가 여기 있다)
        SHOP(null),
        SHOP_UPDATE("/update"),                     // 가게 업데이트
        SHOP_ADMIN("/test/admin/shops"),            // 관리자 가게
        SUBSCRIPTION_SHOP(null),
        SUBSCRIPTION_ADMIN("/test/admin/subscriptions"), // 관리자 구독
        BOARD("/test/board/{id}"),                  // 게시글 상세
        DELIVERY("/my/purchases?purchase={id}"),     // 배송 알림: 구매 내역에서 그 구매의 단계 탭과 상세를 연다
        PAYMENT("/my/purchases");                  // 결제 알림: 구매 내역

        private final String path;

        ReferenceType(String path) {
            this.path = path;
        }

        public String createUrl(Long referenceId) {
            if (path == null) {
                return null;
            }
            if (!path.contains("{id}")) {
                return path;
            }
            if (referenceId == null) {
                return null;
            }
            return path.replace("{id}", referenceId.toString());
        }
    }
}
