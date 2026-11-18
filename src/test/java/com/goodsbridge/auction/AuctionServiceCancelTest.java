package com.goodsbridge.auction;

import org.springframework.context.ApplicationEventPublisher;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 경매 취소(cancel)
 *  - 가게 주인: 입찰이 없는 진행 중 경매만 취소
 *  - 관리자: 입찰이 있어도 취소 (보증금 환불은 스케줄러가 처리)
 */
@ExtendWith(MockitoExtension.class)
class AuctionServiceCancelTest {
    @Mock AuctionRepository auctionRepository;
    @Mock ShopRepository shopRepository;
    @Mock ItemRepository itemRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock SettlementHistoryService settlementHistoryService;
    @Mock com.goodsbridge.auction.BidRepository bidRepository;
    @Mock ApplicationEventPublisher publisher;
    @InjectMocks AuctionService auctionService;

    private Auction activeAuction(Shop shop) {
        return new Auction(shop, mock(Item.class), 10_000, LocalDateTime.now().plusMinutes(30), 1_000);
    }

    // 주의: 내부에서 when()을 쓰므로 다른 when(...).thenReturn(...) 안에서 직접 호출하지 말고 변수에 먼저 담는다.
    private User user(Role role) {
        User user = mock(User.class);
        when(user.getRole()).thenReturn(role);
        return user;
    }

    private AuctionUpdateRequest updateRequest(int start, int deposit) {
        AuctionUpdateRequest request = new AuctionUpdateRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "startPrice", start);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "depositAmount", deposit);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "deadline", LocalDateTime.now().plusDays(2));
        return request;
    }

    @Test
    void update_changesStartPriceDeadlineAndDepositWhenThereAreNoBids() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(1L)).thenReturn(true);
        Auction auction = activeAuction(shop);
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));

        AuctionResponse response = auctionService.update(1L, 1L, updateRequest(30_000, 4_500));

        assertEquals(30_000, response.getStartPrice());
        assertEquals(30_000, response.getCurrentPrice());
        assertEquals(4_500, response.getDepositAmount());
    }

    @Test
    void update_rejectsWhenBidsExist() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(1L)).thenReturn(true);
        Auction auction = activeAuction(shop);
        auction.updateCurrentBid(15_000, mock(User.class));
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));

        assertThrows(BadRequestException.class, () -> auctionService.update(1L, 1L, updateRequest(30_000, 4_500)));

        assertEquals(10_000, auction.getStartPrice());
    }

    @Test
    void update_rejectsNonOwnerAndBadDeposit() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(5L)).thenReturn(false);
        Auction auction = activeAuction(shop);
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));

        assertThrows(ForbiddenException.class, () -> auctionService.update(5L, 1L, updateRequest(30_000, 4_500)));
        // 보증금이 시작가의 10~20% 범위를 벗어나면 검증에서 거절 (경매 조회 전에 실패)
        assertThrows(BadRequestException.class, () -> auctionService.update(1L, 1L, updateRequest(30_000, 100)));
    }

    @Test
    void cancel_allowsOwnerWhenThereAreNoBids() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(1L)).thenReturn(true);
        Auction auction = activeAuction(shop);
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));
        User owner = user(Role.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        AuctionResponse response = auctionService.cancel(1L, 1L);

        assertEquals(Status.CANCELLED, response.getStatus());
        assertEquals(Status.CANCELLED, auction.getStatus());
        verifyNoInteractions(settlementHistoryService);
    }

    @Test
    void cancel_allowsOwnerEvenWhenBidsExist() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(1L)).thenReturn(true);
        Auction auction = activeAuction(shop);
        auction.updateCurrentBid(15_000, mock(User.class));
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));
        User owner = user(Role.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        // 가게 사정으로 입찰이 있어도 취소할 수 있다 (입찰자 보증금은 refundCancelled 가 환불)
        AuctionResponse response = auctionService.cancel(1L, 1L);

        assertEquals(Status.CANCELLED, response.getStatus());
        assertEquals(Status.CANCELLED, auction.getStatus());
    }

    @Test
    void cancel_allowsAdminEvenWhenBidsExist() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(9L)).thenReturn(false);
        Auction auction = activeAuction(shop);
        auction.updateCurrentBid(15_000, mock(User.class));
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));
        User admin = user(Role.ADMIN);
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));

        AuctionResponse response = auctionService.cancel(9L, 1L);

        assertEquals(Status.CANCELLED, response.getStatus());
    }

    @Test
    void cancel_rejectsUserWhoIsNeitherOwnerNorAdmin() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(5L)).thenReturn(false);
        Auction auction = activeAuction(shop);
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));
        User stranger = user(Role.USER);
        when(userRepository.findById(5L)).thenReturn(Optional.of(stranger));

        assertThrows(ForbiddenException.class, () -> auctionService.cancel(5L, 1L));

        assertEquals(Status.ACTIVE, auction.getStatus());
    }

    @Test
    void cancel_rejectsAuctionThatIsAlreadyEnded() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(1L)).thenReturn(true);
        Auction auction = activeAuction(shop);
        auction.end();
        when(auctionRepository.findByIdLock(1L)).thenReturn(Optional.of(auction));
        User owner = user(Role.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        assertThrows(BadRequestException.class, () -> auctionService.cancel(1L, 1L));

        assertEquals(Status.ENDED_FAIL, auction.getStatus());
    }

    @Test
    void cancel_throwsWhenAuctionMissing() {
        when(auctionRepository.findByIdLock(9L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> auctionService.cancel(1L, 9L));

        verifyNoInteractions(userRepository);
    }
}
