
package com.goodsbridge.board;

import com.goodsbridge.item.ItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private String authorNickname;
    private Long itemId;
    private ItemStatus itemStatus;
    private Boolean isSecret;
    private BoardStatus status;
    private LocalDateTime createdAt;
    private List<ReplyResponseDto> replies;

    // 기존 호출부와의 호환성을 위해 유지
    public static BoardResponseDto from(
        Board board,
        Map<Long, String> nicknames
    ) {
        return from(board, nicknames, null);
    }

    // 게시글 상세 조회
    public static BoardResponseDto from(
        Board board,
        Map<Long, String> nicknames,
        ItemStatus itemStatus
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
            itemStatus,
            board.getIsSecret(),
            getReplyStatus(board),
            board.getCreatedAt(),
            replyDtos
        );
    }

    // 기존 호출부와의 호환성을 위해 유지
    public static BoardResponseDto fromForList(
        Board board,
        Map<Long, String> nicknames,
        Long loginUserId,
        boolean isAdmin
    ) {
        return fromForList(
            board, nicknames, loginUserId, isAdmin, Map.of()
        );
    }

    // 게시글 목록 조회
    public static BoardResponseDto fromForList(
        Board board,
        Map<Long, String> nicknames,
        Long loginUserId,
        boolean isAdmin,
        Map<Long, ItemStatus> itemStatuses
    ) {
        boolean isAlwaysPublicBoard =
            board.getBoardType() == BoardType.PRODUCT_REVIEW
                || board.getBoardType() == BoardType.MARKET;

        boolean isCustomerService =
            board.getBoardType() == BoardType.CUSTOMER_SERVICE;

        boolean isAuthor =
            Objects.equals(board.getAuthorId(), loginUserId);

        boolean canReadSecret =
            isAuthor || (isCustomerService && isAdmin);

        // 일반 비밀글은 작성자만 조회할 수 있도록 내용 마스킹
        // PRODUCT_REVIEW와 MARKET은 공개 게시판이므로 마스킹하지 않음
        if (!isAlwaysPublicBoard
            && Boolean.TRUE.equals(board.getIsSecret())
            && !canReadSecret) {

            return new BoardResponseDto(
                board.getId(),
                "비밀글입니다.",
                null,
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                List.of()
            );
        }

        // MARKET 게시글에만 상품 상태 전달
        ItemStatus itemStatus = null;

        if (board.getBoardType() == BoardType.MARKET) {
            itemStatus = itemStatuses.get(board.getItemId());
        }

        return new BoardResponseDto(
            board.getId(),
            board.getTitle(),
            board.getContent(),
            board.getAuthorId(),
            nicknames.getOrDefault(board.getAuthorId(), "알 수 없음"),
            board.getItemId(),
            itemStatus,
            board.getIsSecret(),
            getReplyStatus(board),
            board.getCreatedAt(),
            List.of()
        );
    }
    // 판매자 전용 상품 문의 목록 조회
    public static BoardResponseDto fromForSellerInquiryList(
        Board board,
        Map<Long, String> nicknames
    ) {
        return new BoardResponseDto(
            board.getId(),
            board.getTitle(),
            board.getContent(),
            board.getAuthorId(),
            nicknames.getOrDefault(board.getAuthorId(), "알 수 없음"),
            board.getItemId(),
            null,
            board.getIsSecret(),
            board.getStatus(),
            board.getCreatedAt(),
            List.of()
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
