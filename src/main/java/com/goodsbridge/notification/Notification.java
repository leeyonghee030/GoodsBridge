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

        EXCHANGE_REQUEST,  // 교환 신청
        EXCHANGE_APPROVED, // 교환 승인
        EXCHANGE_REJECTED, // 교환 거절

        AUCTION_BID,       // 경매 입찰
        AUCTION_WIN        // 경매 낙찰
    }

    public enum ReferenceType {
        EXCHANGE, // 교환 상세
        AUCTION   // 경매 상세

        // TODO - url 출력을 위한 enum 메서드 설정
//        EXCHANGE {
//            @Override
//            public String createUrl(Long referenceId) {
//                return "/exchanges/" + referenceId;
//            }
//        },
//
//        AUCTION {
//            @Override
//            public String createUrl(Long referenceId) {
//                return "/auctions/" + referenceId;
//            }
//        };
//        public abstract String createUrl(Long referenceId);

    }
}
