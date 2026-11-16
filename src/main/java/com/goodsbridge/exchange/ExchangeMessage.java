package com.goodsbridge.exchange;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// 교환 수락 후 A, B가 만날 시간을 정하려고 주고받는 채팅 메시지
@Getter
@Entity
@Table(name = "exchange_message_tb")
@NoArgsConstructor
public class ExchangeMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchangeRequestId", nullable = false)
    private ExchangeRequest exchangeRequest;

    @Column(nullable = false)
    private Long senderUserId;

    @Column(nullable = false, length = 500)
    private String content;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder
    public ExchangeMessage(ExchangeRequest exchangeRequest, Long senderUserId, String content) {
        this.exchangeRequest = exchangeRequest;
        this.senderUserId = senderUserId;
        this.content = content;
    }
}
