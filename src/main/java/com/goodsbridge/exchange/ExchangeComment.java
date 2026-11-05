package com.goodsbridge.exchange;


import com.goodsbridge.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "exchange_comment_tb")
@NoArgsConstructor
public class ExchangeComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "exchangeItemId", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private ExchangeItem exchangeItem;

    @JoinColumn(name = "userId", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @Column(nullable = false,length = 255)
    private String content;
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder
    public ExchangeComment(ExchangeItem exchangeItem, User user, String content) {
        this.exchangeItem = exchangeItem;
        this.user = user;
        this.content = content;
    }

    //편의메서드


    //댓글 작성한 사람인지 확인 (수정/삭제 권한 체크용. 관리자 체크는 로그인 유저의 role로 Service에서 따로 확인)
    public boolean isAuthor(Long userId) {
        return this.user.getId().equals(userId);
    }


    //댓글 수정
    public void updateContent(String content) {
        this.content = content;
    }
}
