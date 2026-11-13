package com.goodsbridge.event;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventParticipationRepository extends JpaRepository<EventParticipation, Long> {

    // 내 참가 내역
    @Query("""
    select e from EventParticipation e
    join fetch e.shopEvent
    where e.user.id = :userId
    order by e.createdAt asc
""")
    List<EventParticipation> findByUserId(@Param("userId") Long userId);

    // 이 이벤트에서 내가 참가중인 구매 건들 (추가 구매가 있으면 여러 건, 먼저 산 순서)
    @Query("""
    select e from EventParticipation e
    where e.user.id = :userId and e.shopEvent.id = :shopEventId
    and e.status = 'JOINED'
    order by e.createdAt asc, e.id asc
""")
    List<EventParticipation> findJoinedByUserIdAndShopEventId(@Param("userId") Long userId
        , @Param("shopEventId") Long shopEventId);


    // 다음 번호표 계산용. 취소로 인원이 줄어도 번호표가 겹치지 않도록 지금까지 발급한 최대 번호를 본다
    // 다음 번호표 계산용. 번호표는 1인 1장(수량은 따로 표시). 현재 참가중(JOINED)인 번호의 최대값을 보므로
    // 참가중인 번호와는 절대 겹치지 않고, 취소된 번호는 다음 사람이 이어받을 수 있다
    @Query("select coalesce(max(e.ticketNumber), 0) from EventParticipation e where e.shopEvent.id = :shopEventId and e.status = 'JOINED'")
    int findMaxTicketNumber(@Param("shopEventId") Long shopEventId);

    // 가게 주인용 참가자 명단 (번호표 순)
    @Query("""
    select e from EventParticipation e
    join fetch e.user
    where e.shopEvent.id = :shopEventId
    order by e.ticketNumber asc
""")
    List<EventParticipation> findAllByShopEventId(@Param("shopEventId") Long shopEventId);

    // 가게 정산 보기: 이벤트별/상태별 수량 합계 [이벤트 id, 상태, 수량 합]
    @Query("""
    select e.shopEvent.id, e.status, coalesce(sum(e.quantity), 0)
    from EventParticipation e
    where e.shopEvent.shop.id = :shopId
    group by e.shopEvent.id, e.status
""")
    List<Object[]> sumQuantityByShop(@Param("shopId") Long shopId);

    // 가게 이벤트 정산 내역: 이벤트 참가비로 기록된 정산(SettlementHistory)과 그 결제의 참가 건을 이어서 가져온다
    @Query("""
    select new com.goodsbridge.event.dto.EventSettlementRecordResponse(
        s.createdAt, s.amount, e.shopEvent.id, e.shopEvent.item.title, e.user.nickname, e.quantity)
    from SettlementHistory s, EventParticipation e
    where s.shop.id = :shopId
      and s.type = com.goodsbridge.settlement.SettlementHistoryType.EVENT_FEE
      and e.paymentId = s.payment.paymentId
    order by s.createdAt desc
""")
    List<com.goodsbridge.event.dto.EventSettlementRecordResponse> findSettlementRecords(@Param("shopId") Long shopId);

    // 이벤트의 현재 참가자 (가게 취소 환불, 마감 정산용)
    @Query("select e from EventParticipation e where e.shopEvent.id = :shopEventId and e.status = 'JOINED'")
    List<EventParticipation> findJoinedByShopEventId(@Param("shopEventId") Long shopEventId);

}
