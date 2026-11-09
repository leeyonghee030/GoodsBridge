package com.goodsbridge.board;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class BoardResponseDto {
    private Long id;
    private String title;
    private String content;
    private Long authorId;
    private Long itemId;
    private Boolean isSecret;
    private List<ReplyResponseDto> replies; // 댓글 목록 포함

    public static BoardResponseDto from(Board board) {
        List<ReplyResponseDto> replyDtos = board.getReplies().stream()
            .map(ReplyResponseDto::from)
            .collect(Collectors.toList());

        return new BoardResponseDto(
            board.getId(),
            board.getTitle(),
            board.getContent(),
            board.getAuthorId(),
            board.getItemId(),
            board.getIsSecret(),
            replyDtos
        );
    }
}
