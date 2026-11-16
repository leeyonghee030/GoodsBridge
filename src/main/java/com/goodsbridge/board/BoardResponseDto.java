package com.goodsbridge.board;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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
    private Long itemId;
    private Boolean isSecret;
    private BoardStatus status;
    private List<ReplyResponseDto> replies; // 댓글 목록 포함
    private LocalDateTime createdAt;

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
            board.getStatus(),
            replyDtos,
            board.getCreatedAt()
        );
    }

    public static BoardResponseDto fromForList(
        Board board,
        Long loginUserId
    ) {
        // 비밀글이고 작성자가 아닌 경우
        if (Boolean.TRUE.equals(board.getIsSecret())
            && !Objects.equals(board.getAuthorId(), loginUserId)) {

            return new BoardResponseDto(
                board.getId(),
                "비밀글입니다.",
                null,
                null,
                null,
                true,
                null,
                List.of(),
                null
            );
        }

        // 목록에서는 댓글을 조회하지 않는다.
        return new BoardResponseDto(
            board.getId(),
            board.getTitle(),
            board.getContent(),
            board.getAuthorId(),
            board.getItemId(),
            board.getIsSecret(),
            board.getStatus(),
            List.of(),
            board.getCreatedAt()
        );
    }
}
