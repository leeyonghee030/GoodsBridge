package com.goodsbridge.admin;

import com.goodsbridge.payment.Payment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

// 관리자 대시보드는 플랫폼이 직접 버는 구독 지표만 본다. 가게의 판매·환불·정산은 가게 대시보드(ShopDashboardRepository) 몫이다.
public interface AdminRepository extends Repository<Payment, Long> {

    // ===== 현재 값 (월과 무관) =====

    // 입점 승인을 기다리는 가게
    @Query("""
        select count(s)
        from Shop s
        where s.status = com.goodsbridge.shop.ShopStatus.PENDING
          and s.deleted = false
        """)
    long countPendingShops();

    // 입점이 승인된 가게
    @Query("""
        select count(s)
        from Shop s
        where s.status = com.goodsbridge.shop.ShopStatus.APPROVED
          and s.deleted = false
        """)
    long countApprovedShops();

    // 지금 구독 중인 가게 수 (구독 기간이 아직 끝나지 않은 ACTIVE 구독)
    @Query("""
    select count(distinct sub.shopId)
    from ShopSubscription sub, Shop s
    where s.id = sub.shopId
      and s.status = com.goodsbridge.shop.ShopStatus.APPROVED
      and s.deleted = false
      and sub.status = com.goodsbridge.shop.ShopSubscriptionStatus.ACTIVE
      and sub.endDate >= :today
    """)
    long countActiveSubscribedShops(@Param("today") LocalDate today);

    // 구독이 today ~ until 사이에 끝나는 가게 수 (만료 임박)
    @Query("""
        select count(distinct sub.shopId)
        from ShopSubscription sub
        where sub.status = com.goodsbridge.shop.ShopSubscriptionStatus.ACTIVE
          and sub.endDate >= :today
          and sub.endDate <= :until
        """)
    long countExpiringShops(@Param("today") LocalDate today, @Param("until") LocalDate until);

    // ===== 월별 지표 (startAt 이상 ~ endAt 미만) =====

    // 월 가입자 수. User.createdAt이 Timestamp라서 파라미터도 Timestamp로 받는다.
    @Query("""
        select count(u)
        from User u
        where u.createdAt >= :startAt
          and u.createdAt < :endAt
        """)
    long countSignups(@Param("startAt") Timestamp startAt, @Param("endAt") Timestamp endAt);

    // 월 신규 입점 가게 수: 그 달에 입점(승인)한 가게. 승인 시각이 기록된 가게만 센다.
    // (승인 시각이 생기기 전에 입점한 가게는 신청일로 대신 세면 시연·이전 데이터가 한 달에 몰려 증감률이 왜곡된다)
    @Query("""
        select count(s)
        from Shop s
        where s.status = com.goodsbridge.shop.ShopStatus.APPROVED
          and s.deleted = false
          and s.approvedAt >= :startAt
          and s.approvedAt < :endAt
        """)
    long countNewShops(@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    // 월 구독 이탈 가게 수: 그 달에 구독이 만료되거나 취소된 가게.
    // 끝난 시각(endedAt)이 있으면 그것으로, 이 컬럼이 생기기 전에 끝난 구독은 구독 종료일(endDate)로 본다.
    @Query("""
        select count(distinct sub.shopId)
        from ShopSubscription sub
        where sub.status in (
            com.goodsbridge.shop.ShopSubscriptionStatus.EXPIRED,
            com.goodsbridge.shop.ShopSubscriptionStatus.CANCELLED)
          and (
            (sub.endedAt is not null and sub.endedAt >= :startAt and sub.endedAt < :endAt)
            or (sub.endedAt is null and sub.endDate >= :startDate and sub.endDate < :endDate)
          )
        """)
    long countChurnedShops(@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt,
                           @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // 월 구독 결제 건수 (신규 + 갱신)
    @Query("""
        select count(p)
        from Payment p
        where p.createdAt >= :startAt
          and p.createdAt < :endAt
          and p.purpose = com.goodsbridge.payment.PaymentPurpose.SHOP_SUBSCRIPTION
          and p.status in (
              com.goodsbridge.payment.PaymentStatus.PAID,
              com.goodsbridge.payment.PaymentStatus.PARTIAL_CANCELLED,
              com.goodsbridge.payment.PaymentStatus.RELEASED
          )
        """)
    long countSubscriptionPayments(@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    // 플랫폼 월 매출 = 가게 구독료만 (결제액 - 환불액)
    @Query("""
        select coalesce(sum(p.amount - p.refundedAmount), 0)
        from Payment p
        where p.createdAt >= :startAt
          and p.createdAt < :endAt
          and p.purpose = com.goodsbridge.payment.PaymentPurpose.SHOP_SUBSCRIPTION
          and p.status in (
              com.goodsbridge.payment.PaymentStatus.PAID,
              com.goodsbridge.payment.PaymentStatus.PARTIAL_CANCELLED,
              com.goodsbridge.payment.PaymentStatus.RELEASED
          )
        """)
    long sumSubscriptionRevenue(@Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    // ===== 구독 결제 내역 =====

    // 월별 구독 결제 목록. 결제와 연결된 구독이 있으면 그 가게 이름을 같이 가져온다.
    @Query("""
        select new com.goodsbridge.admin.AdminSubscriptionPaymentItem(
            p.paymentId, u.id, u.nickname, s.name, p.amount, p.refundedAmount, p.status, p.createdAt)
        from Payment p
          join p.user u
          left join ShopSubscription sub on sub.paymentId = p.paymentId
          left join Shop s on s.id = sub.shopId
        where p.purpose = com.goodsbridge.payment.PaymentPurpose.SHOP_SUBSCRIPTION
          and p.status <> com.goodsbridge.payment.PaymentStatus.READY
          and p.createdAt >= :startAt
          and p.createdAt < :endAt
        order by p.createdAt desc, p.id desc
        """)
    List<AdminSubscriptionPaymentItem> findSubscriptionPayments(@Param("startAt") LocalDateTime startAt,
                                                                @Param("endAt") LocalDateTime endAt);

    // 결제와 연결된 구독이 없을 때 보여줄 가게 이름: 결제한 사람이 가진 가게 (삭제된 가게 제외)
    @Query("""
        select s.ownerUserId, s.name
        from Shop s
        where s.ownerUserId in :ownerIds
          and s.deleted = false
        order by s.id
        """)
    List<Object[]> findShopNamesByOwners(@Param("ownerIds") Collection<Long> ownerIds);
}
