
package com.goodsbridge.board;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    private List<ReplyResponseDto> replies;
    private BoardType boardType;

    // 게시글 상세 조회
    public static BoardResponseDto from(
        Board board,
        Map<Long, String> nicknames
    ) {
        List<ReplyResponseDto> replyDtos =
            board.getReplies().stream()
                .sorted(Comparator.comparing(Reply::getCreatedAt).reversed())
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
            getReplyStatus(board),
            board.getCreatedAt(),
            replyDtos,
            board.getBoardType()
        );
    }

    // 게시글 목록 조회
    // 권한이 없는 상품 문의 및 고객센터 게시글은 제목만 노출
    public static BoardResponseDto fromForList(
        Board board,
        Map<Long, String> nicknames,
        boolean canRead
    ) {
        // 권한이 없는 게시글은 제목 외의 정보를 반환하지 않는다.
        if (!canRead) {
            return new BoardResponseDto(
                board.getId(),
                "비밀글입니다.",
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                List.of(),
                board.getBoardType()
            );
        }

        return new BoardResponseDto(
            board.getId(),
            board.getTitle(),
            board.getContent(),
            board.getAuthorId(),
            nicknames.getOrDefault(board.getAuthorId(), "알 수 없음"),
            board.getItemId(),
            board.getIsSecret(),
            getReplyStatus(board),
            board.getCreatedAt(),
            List.of(),
            board.getBoardType()
        );
    }

    // 답변 상태를 관리하는 게시판인지 확인
    private static BoardStatus getReplyStatus(Board board) {

        if (board.getBoardType() == BoardType.PRODUCT_INQUIRY
            || board.getBoardType() == BoardType.CUSTOMER_SERVICE) {
            return board.getStatus();
        }

        return null;
    }
}
