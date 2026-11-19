package com.goodsbridge.board;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReplyRepository extends JpaRepository<Reply, Long> {

    // 게시글 ID로 댓글 목록 조회
    List<Reply> findAllByBoardId(Long boardId);

    // 게시글 ID로 댓글 개수 조회
    long countByBoardId(Long boardId);

    // 게시글의 가장 최근 댓글 (삭제 후 답변 상태를 다시 정할 때)
    Optional<Reply> findFirstByBoardIdOrderByIdDesc(Long boardId);

    // 댓글 ID로 DB에서 직접 삭제
    // JPQL 벌크 DELETE를 사용하므로 영속성 컨텍스트와 연관 컬렉션은 자동 갱신되지 않는다.
    // 삭제된 Reply 객체나 Board.replies 컬렉션을 이후 같은 트랜잭션에서 재사용하지 않도록 주의한다.
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Reply r WHERE r.id = :replyId")
    int deleteByIdInBulk(@Param("replyId") Long replyId);
}
