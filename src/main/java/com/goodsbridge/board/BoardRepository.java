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

    // 제목 또는 내용에 검색어가 포함된 게시글 페이징 조회
    Page<Board> findByTitleContainingOrContentContaining(String title, String content, Pageable pageable);

}

