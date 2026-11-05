package com.goodsbridge.event;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventParticipationRepository extends JpaRepository<EventParticipation, Long> {

    // 내 참가 내역
    @Query("""
    select e from EventParticipation e
    join fetch e.shopEvent
    where e.user.id = :userId
    order by e.createdAt asc
""")
    List<EventParticipation> findByUserId(@Param("userId") Long userId);

    //내 참가 내역 상세 조회
    @Query("""
    select e from EventParticipation e
    join fetch e.shopEvent
    where e.user.id = :userId and  e.shopEvent.id = :shopEventId
    and  e.status = 'JOINED'
""")
    Optional<EventParticipation> findByUserIdAndShopEventId(@Param("userId") Long userId
        , @Param("shopEventId") Long shopEventId);


    //이벱트 중복
    @Query("""
    select case when count(e) > 0  then true else false end
    from EventParticipation e
    where e.shopEvent.id = :shopEventId
          and e.user.id = :userId
          and e.status = 'JOINED'
""")
    boolean existsByShopEvent_IdAndUser_Id(@Param("userId") Long userId
        , @Param("shopEventId") Long shopEventId);


    //소프트삭제
    @Query("""
    update  EventParticipation e
    set e.status = 'CANCELLED'
    where e.shopEvent.id = :shopEventId
          and e.user.id = :userId and e.status = 'JOINED'

""")
    @Modifying
    void cancelByUserIdAndShopEventId(@Param("userId") Long userId
        , @Param("shopEventId") Long shopEventId);




}

