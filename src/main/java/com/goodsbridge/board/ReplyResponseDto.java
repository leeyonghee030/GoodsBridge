package com.goodsbridge.board;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@AllArgsConstructor
public class ReplyResponseDto {
    private Long id;
    private Long boardId;
    private String content;
    private Long authorId;
    private String authorNickname;
    private LocalDateTime createdAt;

    public static ReplyResponseDto from(Reply reply, Map<Long, String> nicknames) {
        return new ReplyResponseDto(
            reply.getId(),
            reply.getBoard().getId(),
            reply.getContent(),
            reply.getAuthorId(),
            nicknames.getOrDefault(reply.getAuthorId(), "알 수 없음"),
            reply.getCreatedAt()
        );
    }
}
