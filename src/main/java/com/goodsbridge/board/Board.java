package com.goodsbridge.board;

import com.goodsbridge._core.exception.BadRequestException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "board_tb")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 100, message = "제목은 100자 이내로 입력해주세요.")
    @Column(nullable = false, length = 100)
    private String title;

    @NotBlank(message = "게시글 내용은 필수입니다.")
    @Size(max = 5000, message = "게시글 내용은 5000자 이내로 입력해주세요.")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Long authorId;

    @Column(name = "item_id")
    private Long itemId; // 상품 문의 및 상품 판매 게시글에 연결된 상품 ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BoardType boardType;

    @Column(nullable = false)
    private Boolean isSecret; // 비밀글 여부 (true: 비밀글, false: 공개글)

    // 양방향 연관관계 설정 (댓글 목록)
    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reply> replies = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoardStatus status; // 상품 문의 및 고객센터 답변 상태

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Board(
        String title,
        String content,
        Long authorId,
        Long itemId,
        BoardType boardType,
        Boolean isSecret
    ) {
        // 필수 입력값 검증
        validateRequiredFields(title, content, authorId);

        // 게시판 유형과 상품 ID 조합 검증
        validateBoardTypeAndItemId(boardType, itemId);

        this.title = title;
        this.content = content;
        this.authorId = authorId;
        this.itemId = itemId;
        this.boardType = boardType;
        // 상품 리뷰는 항상 공개 게시글
        // 상품 리뷰와 판매 게시글은 항상 공개 게시글
        this.isSecret =
            boardType == BoardType.PRODUCT_REVIEW
                || boardType == BoardType.MARKET
                ? false
                : (isSecret != null ? isSecret : false);
        this.status = BoardStatus.PENDING;
    }

    // 게시글 수정 메서드
    public void update(
        String title,
        String content,
        Boolean isSecret
    ) {
        validateRequiredFields(title, content, this.authorId);

        this.title = title;
        this.content = content;

        if (this.boardType == BoardType.PRODUCT_REVIEW
            || this.boardType == BoardType.MARKET) {
            this.isSecret = false;
            return;
        }

        if (isSecret != null) {
            this.isSecret = isSecret;
        }
    }

    // 댓글 작성 시 호출할 상태 변경 메서드
    public void complete() {
        this.status = BoardStatus.COMPLETED;
    }

    public void pending() {
        this.status = BoardStatus.PENDING;
    }

    public void addReply(Reply reply) {
        this.replies.add(reply);
        reply.assignBoard(this);
    }

    public void removeReply(Reply reply) {
        this.replies.remove(reply);
    }

    private void validateBoardTypeAndItemId(
        BoardType boardType,
        Long itemId
    ) {
        if (boardType == null) {
            throw new BadRequestException(
                "게시글 유형(boardType)은 필수 입력값입니다."
            );
        }

        switch (boardType) {
            case PRODUCT_INQUIRY -> {
                if (itemId == null) {
                    throw new BadRequestException(
                        "상품 문의 게시글은 연결할 상품 ID(itemId)가 필수입니다."
                    );
                }
            }

            case PRODUCT_REVIEW -> {
                if (itemId == null) {
                    throw new BadRequestException(
                        "상품 리뷰 게시글은 연결할 상품 ID(itemId)가 필수입니다."
                    );
                }
            }

            case MARKET -> {
                if (itemId == null) {
                    throw new BadRequestException(
                        "상품 판매 게시글은 판매 대상 상품 ID(itemId)가 필수입니다."
                    );
                }
            }

            case CUSTOMER_SERVICE -> {
                if (itemId != null) {
                    throw new BadRequestException(
                        "고객센터 문의 게시글에는 상품 ID(itemId)를 지정할 수 없습니다."
                    );
                }
            }

            case COMMUNITY -> {
                if (itemId != null) {
                    throw new BadRequestException(
                        "커뮤니티 게시글에는 상품 ID(itemId)를 지정할 수 없습니다."
                    );
                }
            }
        }
    }

    private void validateRequiredFields(
        String title,
        String content,
        Long authorId
    ) {
        if (title == null || title.isBlank()) {
            throw new BadRequestException("제목은 필수 입력값입니다.");
        }

        if (title.length() > 100) {
            throw new BadRequestException("제목은 100자 이내로 입력해주세요.");
        }

        if (content == null || content.isBlank()) {
            throw new BadRequestException("게시글 내용은 필수 입력값입니다.");
        }

        if (content.length() > 5000) {
            throw new BadRequestException("게시글 내용은 5000자 이내로 입력해주세요.");
        }

        if (authorId == null) {
            throw new BadRequestException("작성자 ID는 필수 입력값입니다.");
        }
    }
}
