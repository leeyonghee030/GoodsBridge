package com.goodsbridge.notification;

import com.goodsbridge.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    // 알림 제목
    @Column(nullable = false, length = 100)
    private String title;

    // 알림 내용
    @Column(nullable = false, length = 500)
    private String content;

    // 관련 데이터 유형
    @Enumerated(EnumType.STRING)
    private ReferenceType referenceType;

    // 관련 데이터 ID
    private Long referenceId;

    // 읽음 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReadStatus readStatus = ReadStatus.UNREAD;

    // 생성 시간
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // 읽음 처리
    public void markAsRead() {
        this.readStatus = ReadStatus.READ;
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

        AUCTION_BID,            // 경매 입찰
        AUCTION_WIN,            // 경매 낙찰

        PAYMENT_COMPLETED,      // 결제 완료 (배송 준비중)
        DELIVERY_STARTED,       // 배송 시작 (배송중)
        DELIVERY_COMPLETED,     // 배송 완료

        AUCTION_REMAINING_PAYMENT, // 경매 잔금 납부 요청
        REFUND_REJECTED          // 환불 거절
    }

    public enum ReferenceType {
        EXCHANGE("/my/exchange"),                  // 교환 신청 목록
        EXCHANGE_POST("/exchange/{id}"),            // 교환 게시글
        PURCHASE("/my/purchases"),                  // 구매 내역
        REFUND("/my/purchases"),                    // 환불 관련 구매 내역
        EVENT("/test/event/{id}"),                  // 이벤트 상세
        AUCTION("/test/auction"),                   // 경매
        SHOP_UPDATE("/update"),                     // 가게 업데이트
        SHOP_ADMIN("/test/admin/shops"),            // 관리자 가게
        SUBSCRIPTION_ADMIN("/test/admin/subscriptions"), // 관리자 구독
        BOARD("/test/board/{id}"),                  // 게시글 상세
        DELIVERY(null),                              // 배송 알림은 이동 경로 없음
        PAYMENT(null);                               // 결제 알림은 이동 경로 없음

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
