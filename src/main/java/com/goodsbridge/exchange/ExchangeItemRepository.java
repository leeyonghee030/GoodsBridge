package com.goodsbridge.exchange;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExchangeItemRepository extends JpaRepository<ExchangeItem, Long> {
    //1.탐색  items?category 쿼리
    @Query("""
        select e from ExchangeItem e
        join fetch e.item
        join fetch e.user
        join fetch e.shop
        where e.item.category.id =  :categoryId
                 """)
    public List<ExchangeItem> findByItem_Category_Id(@Param("categoryId") Long categoryId);


    //2.userId로 작성된 게시글 목록
    @Query("""
        select e from ExchangeItem e
        join fetch e.user
        join fetch e.item
        join fetch e.shop
        where e.user.id =  :userId
                 """)
    public List<ExchangeItem> findByUser_Id(@Param("userId") Long userId);





}
