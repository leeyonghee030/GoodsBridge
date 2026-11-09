package com.goodsbridge.exchange;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    //3. 카테고리/검색어 필터 (둘 다 선택값)
    @Query("""
        select e from ExchangeItem e
        join fetch e.item
        join fetch e.user
        join fetch e.shop
        where (:categoryId is null or e.item.category.id = :categoryId)
          and (:keyword is null or lower(e.item.title) like lower(concat('%', :keyword, '%'))
               or lower(e.wantDescription) like lower(concat('%', :keyword, '%')))
                 """)
    public List<ExchangeItem> search(@Param("categoryId") Long categoryId, @Param("keyword") String keyword);

    //4. 카테고리/검색어 필터 + 무한스크롤용 페이징
    @Query(value = """
        select e from ExchangeItem e
        join fetch e.item
        join fetch e.user
        join fetch e.shop
        where (:categoryId is null or e.item.category.id = :categoryId)
          and (:keyword is null or lower(e.item.title) like lower(concat('%', :keyword, '%'))
               or lower(e.wantDescription) like lower(concat('%', :keyword, '%')))
                 """,
        countQuery = """
            select count(e) from ExchangeItem e
            where (:categoryId is null or e.item.category.id = :categoryId)
              and (:keyword is null or lower(e.item.title) like lower(concat('%', :keyword, '%'))
                   or lower(e.wantDescription) like lower(concat('%', :keyword, '%')))
                     """)
    public Page<ExchangeItem> search(@Param("categoryId") Long categoryId, @Param("keyword") String keyword, Pageable pageable);





}
