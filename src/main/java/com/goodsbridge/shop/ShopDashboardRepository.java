package com.goodsbridge.shop;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

// 가게 사장님 대시보드용 집계. 모든 쿼리는 사장님 본인 가게(shopIds)로만 좁힌다.
public interface ShopDashboardRepository extends Repository<Shop, Long> {

    // ===== 처리할 일 (월과 무관한 현재 값) =====

    // 내 가게에서 판매된 상품의 환불 승인 대기
    @Query("""
        select count(r)
        from Refund r, Purchase pu
        where pu.paymentId = r.payment.paymentId
          and pu.item.shopId in :shopIds
          and r.status = com.goodsbridge.refund.Status.REQUESTED
        """)
    long countPendingRefunds(@Param("shopIds") Collection<Long> shopIds);

    // 결제는 끝났지만 아직 발송 전인 구매 건 (환불을 요청 중인 건은 발송하지 않으므로 뺀다. 판매 상품 관리의 '배송 준비중'과 같은 기준)
    @Query("""
        select count(pu)
        from Purchase pu
        where pu.item.shopId in :shopIds
          and pu.status = com.goodsbridge.purchase.PurchaseStatus.PAID
          and pu.deliveryStatus = com.goodsbridge.purchase.DeliveryStatus.PREPARING
          and not exists (
              select r.id from Refund r
              where r.payment.paymentId = pu.paymentId
                and r.status = com.goodsbridge.refund.Status.REQUESTED)
        """)
    long countPendingShipments(@Param("shopIds") Collection<Long> shopIds);

    @Query("""
        select count(a)
        from Auction a
        where a.shop.id in :shopIds
          and a.status = com.goodsbridge.auction.Status.ACTIVE
        """)
    long countActiveAuctions(@Param("shopIds") Collection<Long> shopIds);

    // 낙찰 성공, 낙찰 실패 (누적). 경매 관리의 '종료' 탭과 같은 기준이라 취소된 경매는 뺀다.
    @Query("""
        select count(a)
        from Auction a
        where a.shop.id in :shopIds
          and a.status in (
            com.goodsbridge.auction.Status.ENDED_SUCCESS,
            com.goodsbridge.auction.Status.ENDED_FAIL
          )
        """)
    long countEndedAuctions(@Param("shopIds") Collection<Long> shopIds);

    // 낙찰됐지만 아직 잔금을 내지 않은 경매
    @Query("""
        select count(a)
        from Auction a
        where a.shop.id in :shopIds
          and a.status = com.goodsbridge.auction.Status.ENDED_SUCCESS
          and a.balancePaymentId is null
        """)
    long countUnpaidBalanceAuctions(@Param("shopIds") Collection<Long> shopIds);

    // 모집 중인 이벤트
    @Query("""
        select count(e)
        from ShopEvent e
        where e.shop.id in :shopIds
          and e.status = com.goodsbridge.event.ShopEventStatus.OPEN
        """)
    long countOpenEvents(@Param("shopIds") Collection<Long> shopIds);

    // 정산 예정액: 낙찰됐지만 잔금을 아직 내지 않은 경매의 낙찰가 합계 (잔금이 결제되면 낙찰가 전액이 정산된다)
    @Query("""
        select coalesce(sum(a.currentPrice), 0)
        from Auction a
        where a.shop.id in :shopIds
          and a.status = com.goodsbridge.auction.Status.ENDED_SUCCESS
          and a.balancePaymentId is null
        """)
    long sumPendingSettlement(@Param("shopIds") Collection<Long> shopIds);

    // 지금 구독 중인 가게 수 (구독 기간이 아직 끝나지 않은 ACTIVE 구독)
    @Query("""
        select count(distinct sub.shopId)
        from ShopSubscription sub
        where sub.shopId in :shopIds
          and sub.status = com.goodsbridge.shop.ShopSubscriptionStatus.ACTIVE
          and sub.endDate >= :today
        """)
    long countActiveSubscribedShops(@Param("shopIds") Collection<Long> shopIds, @Param("today") LocalDate today);

    // 구독 중인 가게 가운데 가장 먼저 끝나는 구독의 종료일 (구독 중인 가게가 없으면 null)
    @Query("""
        select min(sub.endDate)
        from ShopSubscription sub
        where sub.shopId in :shopIds
          and sub.status = com.goodsbridge.shop.ShopSubscriptionStatus.ACTIVE
          and sub.endDate >= :today
        """)
    LocalDate findNearestSubscriptionEnd(@Param("shopIds") Collection<Long> shopIds, @Param("today") LocalDate today);

    // ===== 월별 지표 (startAt 이상 ~ endAt 미만) =====

    // 월 판매 건수 (환불된 건 제외)
    @Query("""
        select count(pu)
        from Purchase pu
        where pu.item.shopId in :shopIds
          and pu.createdAt >= :startAt
          and pu.createdAt < :endAt
          and pu.status <> com.goodsbridge.purchase.PurchaseStatus.REFUNDED
        """)
    long countSales(@Param("shopIds") Collection<Long> shopIds,
                    @Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    // 월 매출: 일반 판매 대금 + 교환 수수료 + 노쇼 몰수금 + 경매 낙찰가 + 이벤트 참가비
    @Query("""
        select coalesce(sum(s.amount), 0)
        from SettlementHistory s
        where s.shop.id in :shopIds
          and s.createdAt >= :startAt
          and s.createdAt < :endAt
          and s.type in (
              com.goodsbridge.settlement.SettlementHistoryType.PURCHASE_FEE,
              com.goodsbridge.settlement.SettlementHistoryType.EXCHANGE_FEE,
              com.goodsbridge.settlement.SettlementHistoryType.NO_SHOW_FEE,
              com.goodsbridge.settlement.SettlementHistoryType.AUCTION_SALE,
              com.goodsbridge.settlement.SettlementHistoryType.EVENT_FEE
          )
        """)
    long sumRevenue(@Param("shopIds") Collection<Long> shopIds,
                    @Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    // 월 매출을 종류별로 나눈 값 ({종류, 합계}). 합치면 sumRevenue와 같다.
    @Query("""
        select s.type, coalesce(sum(s.amount), 0)
        from SettlementHistory s
        where s.shop.id in :shopIds
          and s.createdAt >= :startAt
          and s.createdAt < :endAt
          and s.type in (
              com.goodsbridge.settlement.SettlementHistoryType.PURCHASE_FEE,
              com.goodsbridge.settlement.SettlementHistoryType.EXCHANGE_FEE,
              com.goodsbridge.settlement.SettlementHistoryType.NO_SHOW_FEE,
              com.goodsbridge.settlement.SettlementHistoryType.AUCTION_SALE,
              com.goodsbridge.settlement.SettlementHistoryType.EVENT_FEE
          )
        group by s.type
        """)
    List<Object[]> sumRevenueByType(@Param("shopIds") Collection<Long> shopIds,
                                    @Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    // 월 환불 금액: 그 달에 환불을 요청해서 승인된 내 가게 구매 건의 환불 금액 합계 (판매 상품 관리의 환불 탭과 같은 요청일 기준).
    // 결제(Payment)의 환불액을 더하면 경매 낙찰가 전액 환불에서 보증금 결제분이 빠져 환불 내역보다 적게 나온다.
    @Query("""
        select coalesce(sum(r.requestedAmount), 0)
        from Refund r, Purchase pu
        where pu.paymentId = r.payment.paymentId
          and pu.item.shopId in :shopIds
          and r.status = com.goodsbridge.refund.Status.APPROVED
          and r.createdAt >= :startAt
          and r.createdAt < :endAt
        """)
    long sumRefundedAmount(@Param("shopIds") Collection<Long> shopIds,
                           @Param("startAt") Timestamp startAt, @Param("endAt") Timestamp endAt);
}
