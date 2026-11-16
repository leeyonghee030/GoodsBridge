package com.goodsbridge.board;

import com.goodsbridge.item.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {
    // 게시글과 연관된 댓글을 페치 조인으로 함께 조회
    @Query("SELECT DISTINCT b FROM Board b LEFT JOIN FETCH b.replies WHERE b.id = :id")
    Optional<Board> findByIdWithReplies(@Param("id") Long id);

    // 공개 게시판 유형에 해당하는 게시글 페이징 조회
    Page<Board> findByBoardTypeIn(
        List<BoardType> boardTypes,
        Pageable pageable
    );

    // 공개 게시판 유형에서 제목, 내용 또는 작성자 닉네임 검색
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

    // 제목 또는 내용에 검색어가 포함된 게시글 페이징 조회
    Page<Board> findByTitleContainingOrContentContaining(
        String title,
        String content,
        Pageable pageable
    );

    // 상품 ID와 게시글 유형으로 게시글 페이징 조회
    Page<Board> findByItemIdAndBoardType(
        Long itemId,
        BoardType boardType,
        Pageable pageable
    );


    // 특정 상품의 특정 유형 게시글 중 작성자 본인의 게시글 조회
    Page<Board> findByItemIdAndBoardTypeAndAuthorId(
        Long itemId,
        BoardType boardType,
        Long authorId,
        Pageable pageable
    );

    // 특정 게시판 유형에서 작성자 본인의 게시글 조회
    Page<Board> findByBoardTypeAndAuthorId(
        BoardType boardType,
        Long authorId,
        Pageable pageable
    );

    // 특정 게시판 유형의 전체 게시글 조회 (관리자용)
    Page<Board> findByBoardType(
        BoardType boardType,
        Pageable pageable
    );

    // 공개 게시판 유형에 해당하는 게시글 페이징 조회
// MARKET 게시글은 판매 중이거나 판매 완료된 상품만 조회
    @Query("""
    SELECT b
    FROM Board b
    WHERE b.boardType IN :boardTypes
    AND (
        b.boardType <> :marketType
        OR EXISTS (
            SELECT i.id
            FROM Item i
            WHERE i.id = b.itemId
            AND i.deleted = false
            AND i.status IN :visibleItemStatuses
        )
    )
    """)
    Page<Board> findPublicBoards(
        @Param("boardTypes") List<BoardType> boardTypes,
        @Param("marketType") BoardType marketType,
        @Param("visibleItemStatuses") List<ItemStatus> visibleItemStatuses,
        Pageable pageable
    );

    // 공개 게시판 유형에서 제목, 내용 또는 작성자 닉네임 검색
// MARKET 게시글은 판매 중이거나 판매 완료된 상품만 조회
    @Query("""
    SELECT b
    FROM Board b
    JOIN User u ON u.id = b.authorId
    WHERE b.boardType IN :boardTypes
    AND (
        b.boardType <> :marketType
        OR EXISTS (
            SELECT i.id
            FROM Item i
            WHERE i.id = b.itemId
            AND i.deleted = false
            AND i.status IN :visibleItemStatuses
        )
    )
    AND (
        b.title LIKE %:keyword%
        OR b.content LIKE %:keyword%
        OR u.nickname LIKE %:keyword%
    )
    """)
    Page<Board> findPublicBoardsByKeyword(
        @Param("keyword") String keyword,
        @Param("boardTypes") List<BoardType> boardTypes,
        @Param("marketType") BoardType marketType,
        @Param("visibleItemStatuses") List<ItemStatus> visibleItemStatuses,
        Pageable pageable
    );
}

