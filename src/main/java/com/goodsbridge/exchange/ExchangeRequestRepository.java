package com.goodsbridge.exchange;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExchangeRequestRepository extends JpaRepository<ExchangeRequest,Long> {
    // 내교환 현황 - userid로  A(게시글 주인)인 경우랑 B(제안자)비교
    @Query("""
    select e from ExchangeRequest e
    join fetch e.exchangeItemA
    join fetch e.itemB
    join fetch e.shop
    where e.exchangeItemA.user.id = :userId or e.itemB.sellerUserId = :userId
""")
    public List<ExchangeRequest> findByExchangeItemA_User_IdOrItemB_SellerUserId(@Param("userId") Long userId);


    // 가게 사장님 화면: 내 가게에서 만나기로 한(확정 이후) 교환만. 다른 가게의 교환은 절대 포함되지 않는다.
    // keyword(소문자, 없으면 빈 문자열)는 교환번호 / 두 상품 제목 / 두 사람 닉네임 중 하나라도 포함되면 검색된다.
    @Query("""
    select e from ExchangeRequest e
    join fetch e.exchangeItemA ea
    join fetch ea.user ua
    join fetch ea.item ia
    join fetch e.itemB ib
    join fetch e.shop s
    where s.ownerUserId = :ownerUserId
    and e.status in :statuses
    and (:keyword = ''
        or cast(e.id as string) like concat('%', :keyword, '%')
        or lower(ia.title) like concat('%', :keyword, '%')
        or lower(ib.title) like concat('%', :keyword, '%')
        or lower(ua.nickname) like concat('%', :keyword, '%')
        or exists (select 1 from User ub where ub.id = ib.sellerUserId and lower(ub.nickname) like concat('%', :keyword, '%')))
    order by e.id desc
""")
    public List<ExchangeRequest> searchShopExchanges(@Param("ownerUserId") Long ownerUserId,
                                                     @Param("statuses") List<ExchangeRequestStatus> statuses,
                                                     @Param("keyword") String keyword,
                                                     Pageable pageable);

    // 오늘 만나기로 확정(약속 시간 확정)된, 아직 방문 확인 전인 교환. 약속 시간 순.
    @Query("""
    select e from ExchangeRequest e
    join fetch e.exchangeItemA ea
    join fetch ea.user
    join fetch ea.item
    join fetch e.itemB
    join fetch e.shop s
    where s.ownerUserId = :ownerUserId
    and e.status = 'CONFIRMED'
    and e.meetingConfirmed = true
    and e.meetingAt >= :from and e.meetingAt < :to
    order by e.meetingAt asc
""")
    public List<ExchangeRequest> findShopMeetingsBetween(@Param("ownerUserId") Long ownerUserId,
                                                         @Param("from") java.time.LocalDateTime from,
                                                         @Param("to") java.time.LocalDateTime to);

    @Query("""
    select count(e) from ExchangeRequest e
    where e.shop.ownerUserId = :ownerUserId and e.status in :statuses
""")
    public long countShopExchanges(@Param("ownerUserId") Long ownerUserId,
                                   @Param("statuses") List<ExchangeRequestStatus> statuses);

    //existsByItemBAndStatusIn 내 아이템으로 다른 PENDING/CONFIRMED 제안에 이미 쓰이고 있으면 중복 제안 불가
    @Query("""
        select case when count(e) > 0 then true else false end
        from ExchangeRequest e where e.itemB.id = :itemId
        and e.status in :statuses
""")
    public boolean existsByItemBAndStatusIn(@Param("itemId")Long itemId
        ,@Param("statuses") List<ExchangeRequestStatus> statuses);


    // 교환 게시글 삭제 시 거절/취소/만료된 지난 신청 기록도 함께 삭제
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ExchangeRequest r where r.exchangeItemA.id = :exchangeItemId")
    void deleteByExchangeItemId(@Param("exchangeItemId") Long exchangeItemId);

    //수락했으니, 같은 A 게시글에 걸린 나머지 경쟁 제안(PENDING)들을 자동취소 대상으로 찾는 것
    //itemB 중복 제안은 propose 시점에 이미 막아두므로 여기선 exchangeItemA 기준만 확인하면 됨
    @Query("""
    select e from ExchangeRequest e
    where e.status = 'PENDING'
    and e.exchangeItemA = :item
""")
    public List<ExchangeRequest> findActiveRequestsByItem(@Param("item") ExchangeItem item);

    // 둘 다 방문 체크된 뒤 유예 시간이 지나 정산할 차례인 교환
    @Query("""
    select e from ExchangeRequest e
    where e.status = 'CONFIRMED' and e.bothCheckedAt is not null and e.bothCheckedAt <= :limit
""")
    public List<ExchangeRequest> findReadyToComplete(@Param("limit") java.time.LocalDateTime limit);

    //기한 지난 것 찾기
    @Query("""
    select e from ExchangeRequest e
    where e.status in ('PENDING','CONFIRMED') and e.deadline < CURRENT_TIMESTAMP
""")
    public List<ExchangeRequest> findByStatusAndDeadlineBefore();

    // 교환 게시글(ExchangeItem)별 제안받은 횟수 - 인기순 정렬용
    @Query("""
        select e.exchangeItemA.id as exchangeItemId, count(e) as requestCount
        from ExchangeRequest e
        where e.exchangeItemA.id in :exchangeItemIds
        group by e.exchangeItemA.id
        """)
    List<ExchangeItemRequestCountView> countGroupedByExchangeItemId(@Param("exchangeItemIds") List<Long> exchangeItemIds);

    interface ExchangeItemRequestCountView {
        Long getExchangeItemId();
        Long getRequestCount();
    }


}
