package com.goodsbridge.board;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "boards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Long authorId;

    @Column(name = "item_id")
    private Long itemId; // 상품 Q&A나 리뷰인 경우 연관된 상품 ID (공지사항 등은 null 허용)

    @Column(nullable = false)
    private Boolean isSecret; // 비밀글 여부 (true: 비밀글, false: 공개글)

    // 양방향 연관관계 설정 (댓글 목록)
    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reply> replies = new ArrayList<>();

    @Builder
    public Board(String title, String content, Long authorId, Long itemId, Boolean isSecret) {
        this.title = title;
        this.content = content;
        this.authorId = authorId;
        this.itemId = itemId;
        this.isSecret = isSecret != null ? isSecret : false;
    }

    // 게시글 수정 메서드
    public void update(String title, String content, Long itemId, Boolean isSecret) {
        this.title = title;
        this.content = content;
        this.itemId = itemId;
        if (isSecret != null) {
            this.isSecret = isSecret;
        }
    }
}
