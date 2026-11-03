package com.goodsbridge.exchange;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExchangeRequestRepository extends JpaRepository<ExchangeRequest,Long> {
    // 내교환 현황 - userid로  A(게시글 주인)인 경우랑 B(제안자)비교
    @Query("""
    select e from ExchangeRequest e
    join fetch e.exchangeItemA
    join fetch e.exchangeItemB
    join fetch e.shop
    where e.exchangeItemA.user.id = :userId or e.exchangeItemB.user.id = :userId
""")
    public List<ExchangeRequest> findByExchangeItemA_User_IdOrExchangeItemB_User_Id(@Param("userId") Long userId);


    //existsByItemBAndStatusIn 내 아이템으로 다른 PENDING/CONFIRMED 제안에 이미 쓰이고 있으면 중복 제안 불가
    @Query("""
        select case when count(e) > 0 then true else false end
        from ExchangeRequest e where e.itemB.id = :itemId
        and e.status in :statuses
""")
    public boolean existsByItemBAndStatusIn(@Param("itemId")Long itemId
        ,@Param("statuses") List<ExchangeRequestStatus> statuses);


    //수락했으니, 같은 A 게시글에 걸린 나머지 경쟁 제안(PENDING)들을 자동취소 대상으로 찾는 것
    //itemB 중복 제안은 propose 시점에 이미 막아두므로 여기선 exchangeItemA 기준만 확인하면 됨
    @Query("""
    select e from ExchangeRequest e
    where e.status = 'PENDING'
    and e.exchangeItemA = :item
""")
    public List<ExchangeRequest> findActiveRequestsByItem(@Param("item") ExchangeItem item);

    //기한 지난 것 찾기
    @Query("""
    select e from ExchangeRequest e
    where e.status = 'PENDING' and e.deadline < CURRENT_TIMESTAMP
""")
    public List<ExchangeRequest> findByStatusAndDeadlineBefore();

}
