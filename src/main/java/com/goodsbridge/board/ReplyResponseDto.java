package com.goodsbridge.board;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ReplyResponseDto {
    private Long id;
    private Long boardId;
    private String content;
    private Long authorId;
    private LocalDateTime createdAt;

    public static ReplyResponseDto from(Reply reply) {
        return new ReplyResponseDto(reply.getId(), reply.getBoard().getId(), reply.getContent(), reply.getAuthorId(), reply.getCreatedAt());
    }
}
