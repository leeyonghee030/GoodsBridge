package com.goodsbridge.board;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReplyResponseDto {
    private Long id;
    private Long boardId;
    private String content;
    private Long authorId;

    public static ReplyResponseDto from(Reply reply) {
        return new ReplyResponseDto(reply.getId(), reply.getBoard().getId(), reply.getContent(), reply.getAuthorId());
    }
}
