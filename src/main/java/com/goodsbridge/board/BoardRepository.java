package com.goodsbridge.board;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {
    // 게시글과 연관된 댓글을 페치 조인으로 함께 조회
    @Query("SELECT DISTINCT b FROM Board b LEFT JOIN FETCH b.replies WHERE b.id = :id")
    Optional<Board> findByIdWithReplies(@Param("id") Long id);

    // 제목, 내용 또는 작성자 닉네임에 검색어가 포함된 게시글 페이징 조회
    @Query("SELECT b FROM Board b JOIN User u ON u.id = b.authorId " +
        "WHERE b.title LIKE %:keyword% OR b.content LIKE %:keyword% OR u.nickname LIKE %:keyword%")
    Page<Board> findByKeyword(@Param("keyword") String keyword, Pageable pageable);

}

