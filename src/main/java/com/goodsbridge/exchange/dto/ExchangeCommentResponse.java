package com.goodsbridge.exchange.dto;

import com.goodsbridge.exchange.ExchangeComment;

import lombok.Getter;
import lombok.NoArgsConstructor;


import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class ExchangeCommentResponse {
    private Long id;
    private Long userId;
    private String username;
    private String content;
    private LocalDateTime createdAt;

    public ExchangeCommentResponse(ExchangeComment comment) {
        this.id = comment.getId();
        this.userId = comment.getUser().getId();
        this.username = comment.getUser().getNickname();
        this.content = comment.getContent();
        this.createdAt = comment.getCreatedAt();
    }
}
