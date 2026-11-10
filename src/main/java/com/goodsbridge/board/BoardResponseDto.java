package com.goodsbridge.board;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BoardResponseDto {
    private Long id;
    private String title;
    private String content;
    private Long authorId;
    private String authorNickname;
    private Long itemId;
    private Boolean isSecret;
    private BoardStatus status;
    private LocalDateTime createdAt;
    private List<ReplyResponseDto> replies; // 댓글 목록 포함

    public static BoardResponseDto from(Board board, Map<Long, String> nicknames) {
        List<ReplyResponseDto> replyDtos = board.getReplies().stream()
            .sorted(Comparator.comparing(Reply::getCreatedAt).reversed()) // 최신 댓글이 먼저 보이도록
            .map(reply -> ReplyResponseDto.from(reply, nicknames))
            .collect(Collectors.toList());

        return new BoardResponseDto(
            board.getId(),
            board.getTitle(),
            board.getContent(),
            board.getAuthorId(),
            nicknames.getOrDefault(board.getAuthorId(), "알 수 없음"),
            board.getItemId(),
            board.getIsSecret(),
            board.getStatus(),
            board.getCreatedAt(),
            replyDtos
        );
    }

}
