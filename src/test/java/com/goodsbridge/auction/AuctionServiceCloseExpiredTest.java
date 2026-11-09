package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.settlement.SettlementHistoryService;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 경매 마감
 *  - closeExpired: 스케줄러가 호출하는 단건 마감
 *  - close: 가게 주인/관리자가 호출하는 수동 마감 (권한 검증 포함)
 * REQUIRES_NEW 트랜잭션은 테스트 트랜잭션의 미커밋 데이터를 볼 수 없어 DB 통합 테스트 대신 Mockito로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AuctionServiceCloseExpiredTest {
    @Mock AuctionRepository auctionRepository;
    @Mock ShopRepository shopRepository;
    @Mock ItemRepository itemRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock SettlementHistoryService settlementHistoryService;
    @InjectMocks AuctionService auctionService;

    private Auction expiredAuction(Shop shop) {
        return new Auction(shop, null, 10_000, LocalDateTime.now().minusMinutes(1), 1_000);
    }

    // close()는 AuctionResponse를 만들 때 shop/item id를 읽으므로 item도 필요하다
    private Auction expiredAuction(Shop shop, Item item) {
        return new Auction(shop, item, 10_000, LocalDateTime.now().minusMinutes(1), 1_000);
    }

    private User user(Role role) {
        User user = mock(User.class);
        when(user.getRole()).thenReturn(role);
        return user;
    }

    // ===================== closeExpired (스케줄러) =====================

    @Test
    void closeExpired_endsAuctionWithoutBidsAsFailAndSkipsSettlement() {
        Auction auction = expiredAuction(mock(Shop.class));
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));

        auctionService.closeExpired(1L);

        assertEquals(Status.ENDED_FAIL, auction.getStatus());
        assertNull(auction.getBalanceDueAt());
        verifyNoInteractions(settlementHistoryService);
    }

    @Test
    void closeExpired_doesNotSettleWhenWinnerExists() {
        Shop shop = mock(Shop.class);
        Auction auction = expiredAuction(shop);
        auction.updateCurrentBid(15_000, mock(User.class));
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));

        auctionService.closeExpired(1L);

        assertEquals(Status.ENDED_SUCCESS, auction.getStatus());
        // 낙찰이면 잔금 결제 기한이 정해진다.
        assertNotNull(auction.getBalanceDueAt());
        // 낙찰 정산은 잔금 결제(payBalance) 성공 후에만 처리한다.
        verifyNoInteractions(settlementHistoryService);
    }

    @Test
    void closeExpired_ignoresAuctionAlreadyEnded() {
        Auction auction = expiredAuction(mock(Shop.class));
        auction.end();
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));

        auctionService.closeExpired(1L);   // 예외 없이 통과 (스케줄러 중복 실행 대비)

        verifyNoInteractions(settlementHistoryService);
    }

    @Test
    void closeExpired_throwsWhenAuctionMissing() {
        when(auctionRepository.findByIdLock(9L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> auctionService.closeExpired(9L));
    }

    @Test
    void closeExpired_rejectsAuctionWhoseDeadlineHasNotPassed() {
        // 안티 스나이핑으로 마감이 연장된 경매가 스케줄러 조회 직후 다시 미래 시각이 된 경우
        Auction auction = new Auction(mock(Shop.class), null, 10_000, LocalDateTime.now().plusMinutes(5), 1_000);
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));

        assertThrows(BadRequestException.class, () -> auctionService.closeExpired(1L));

        assertEquals(Status.ACTIVE, auction.getStatus());
    }

    // ===================== close (수동 마감 + 권한) =====================

    @Test
    void close_allowsShopOwnerAndEndsAuctionWithoutBidsAsFail() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(1L)).thenReturn(true);
        Auction auction = expiredAuction(shop, mock(Item.class));
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(Role.USER)));

        AuctionResponse response = auctionService.close(1L, 1L);

        assertEquals(Status.ENDED_FAIL, response.getStatus());
        verifyNoInteractions(settlementHistoryService);
    }

    @Test
    void close_withWinnerReturnsEndedSuccessWithBalanceInfo() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(1L)).thenReturn(true);
        User winner = mock(User.class);
        when(winner.getId()).thenReturn(7L);
        Auction auction = expiredAuction(shop, mock(Item.class));
        auction.updateCurrentBid(15_000, winner);
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(Role.USER)));

        AuctionResponse response = auctionService.close(1L, 1L);

        assertEquals(Status.ENDED_SUCCESS, response.getStatus());
        assertEquals(7L, response.getCurrentWinnerUserId());
        assertEquals(14_000, response.getBalanceAmount());   // 낙찰가 15_000 - 보증금 1_000
        assertNotNull(response.getBalanceDueAt());
    }

    @Test
    void close_allowsAdminEvenIfNotShopOwner() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(9L)).thenReturn(false);
        Auction auction = expiredAuction(shop, mock(Item.class));
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user(Role.ADMIN)));

        AuctionResponse response = auctionService.close(9L, 1L);

        assertEquals(Status.ENDED_FAIL, response.getStatus());
    }

    @Test
    void close_rejectsUserWhoIsNeitherOwnerNorAdmin() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(5L)).thenReturn(false);
        Auction auction = expiredAuction(shop, mock(Item.class));
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(Role.USER)));

        assertThrows(ForbiddenException.class, () -> auctionService.close(5L, 1L));

        assertEquals(Status.ACTIVE, auction.getStatus());
    }

    @Test
    void close_throwsWhenAuctionMissing() {
        when(auctionRepository.findByIdLock(9L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> auctionService.close(1L, 9L));

        verifyNoInteractions(userRepository);
    }

    @Test
    void close_throwsWhenUserMissing() {
        Auction auction = expiredAuction(mock(Shop.class), mock(Item.class));
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));
        when(userRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> auctionService.close(5L, 1L));

        assertEquals(Status.ACTIVE, auction.getStatus());
    }

    @Test
    void close_rejectsAuctionWhoseDeadlineHasNotPassed() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(1L)).thenReturn(true);
        Auction auction = new Auction(shop, mock(Item.class), 10_000, LocalDateTime.now().plusMinutes(5), 1_000);
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(Role.USER)));

        assertThrows(BadRequestException.class, () -> auctionService.close(1L, 1L));

        assertEquals(Status.ACTIVE, auction.getStatus());
    }
}
