package com.goodsbridge.exchange;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExchangeCommentRepository extends JpaRepository<ExchangeComment, Long> {

    //List 댓글목록

    @Query("""
    select e from ExchangeComment e
    join fetch e.user
    where e.exchangeItem.id = :itemId
    order by e.createdAt asc
""")
    List<ExchangeComment> getCommentList(@Param("itemId") Long itemId);

}
