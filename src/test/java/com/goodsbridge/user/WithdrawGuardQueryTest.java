package com.goodsbridge.user;

import com.goodsbridge.auction.AuctionRepository;
import com.goodsbridge.event.EventParticipationRepository;
import com.goodsbridge.exchange.ExchangeRequestRepository;
import com.goodsbridge.purchase.PurchaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// 탈퇴 차단용 JPQL이 실제로 실행되는지 확인한다 (데이터가 없으면 모두 false)
@SpringBootTest
@Transactional
class WithdrawGuardQueryTest {

    @Autowired ExchangeRequestRepository exchangeRequestRepository;
    @Autowired AuctionRepository auctionRepository;
    @Autowired EventParticipationRepository eventParticipationRepository;
    @Autowired PurchaseRepository purchaseRepository;

    @Test
    void 쿼리들이_오류없이_실행된다() {
        assertThat(exchangeRequestRepository.existsOngoingByUserId(-1L)).isFalse();
        assertThat(auctionRepository.existsOngoingByBidderId(-1L)).isFalse();
        assertThat(eventParticipationRepository.existsOngoingByUserId(-1L)).isFalse();
        assertThat(purchaseRepository.existsUndeliveredByUserId(-1L)).isFalse();
    }
}
