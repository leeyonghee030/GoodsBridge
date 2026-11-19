
package com.goodsbridge.board;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {

    // 게시글과 연관된 댓글을 페치 조인으로 함께 조회
    @Query("""
        SELECT DISTINCT b
        FROM Board b
        LEFT JOIN FETCH b.replies
        WHERE b.id = :id
        """)
    Optional<Board> findByIdWithReplies(@Param("id") Long id);

    // 제목, 내용 또는 작성자 닉네임 검색
    @Query("""
        SELECT b
        FROM Board b
        JOIN User u ON u.id = b.authorId
        WHERE b.boardType IN :boardTypes
        AND (
            b.title LIKE %:keyword%
            OR b.content LIKE %:keyword%
            OR u.nickname LIKE %:keyword%
        )
        """)
    Page<Board> findByKeyword(
        @Param("keyword") String keyword,
        @Param("boardTypes") List<BoardType> boardTypes,
        Pageable pageable
    );

    // 한 상품의 리뷰 (상품 상세 화면용)
    @Query("""
        SELECT b FROM Board b
        WHERE b.boardType = com.goodsbridge.board.BoardType.PRODUCT_REVIEW
        AND b.itemId = :itemId
        """)
    Page<Board> findReviewsByItemId(@Param("itemId") Long itemId, Pageable pageable);

    // 상품 화면의 문의. 대상을 기록하기 전에 쓰인 문의(targetType 이 비어 있음)도 상품 문의로 본다.
    @Query("""
        SELECT b FROM Board b
        WHERE b.boardType = com.goodsbridge.board.BoardType.PRODUCT_INQUIRY
        AND (b.targetType IS NULL OR b.targetType = com.goodsbridge.board.InquiryTargetType.ITEM)
        AND b.itemId = :itemId
        """)
    Page<Board> findItemInquiries(@Param("itemId") Long itemId, Pageable pageable);

    // 이벤트/경매 화면의 문의
    @Query("""
        SELECT b FROM Board b
        WHERE b.boardType = com.goodsbridge.board.BoardType.PRODUCT_INQUIRY
        AND b.targetType = :targetType
        AND b.targetId = :targetId
        """)
    Page<Board> findInquiriesByTarget(
        @Param("targetType") InquiryTargetType targetType,
        @Param("targetId") Long targetId,
        Pageable pageable
    );

    boolean existsByPurchaseId(Long purchaseId);

    // 내가 쓴 리뷰: [구매 id, 게시글 id]
    @Query("""
        SELECT b.purchaseId, b.id FROM Board b
        WHERE b.authorId = :authorId
        AND b.boardType = com.goodsbridge.board.BoardType.PRODUCT_REVIEW
        AND b.purchaseId IS NOT NULL
        """)
    List<Object[]> findMyReviewIds(@Param("authorId") Long authorId);

    // 특정 게시판 유형의 전체 게시글 조회
    Page<Board> findByBoardType(
        BoardType boardType,
        Pageable pageable
    );
}
