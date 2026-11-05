package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.shop.ShopRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuctionServiceTest {

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Test
    void create_initializesCurrentPriceAndActiveStatus() {
        AuctionResponse response = auctionService.create(1L, 1L,
            request(1L, 50_000, 5_000, 10));

        assertThat(response.getId()).isNotNull();
        assertThat(response.getCurrentPrice()).isEqualTo(50_000);
        assertThat(response.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(response.getShopId()).isEqualTo(1L);
        assertThat(response.getItemId()).isEqualTo(1L);
    }

    @Test
    void create_rejectsNonOwner() {
        assertThatThrownBy(() -> auctionService.create(2L, 1L,
            request(1L, 50_000, 5_000, 10)))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_rejectsInvalidStartPriceDepositAndDeadline() {
        assertThatThrownBy(() -> auctionService.create(1L, 1L,
            request(1L, 0, 5_000, 10)))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> auctionService.create(1L, 1L,
            request(1L, 50_000, 1_000, 10)))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> auctionService.create(1L, 1L,
            request(1L, 50_000, 5_000, -1)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void findAll_filtersByStatusAndFindByIdRejectsMissingAuction() {
        auctionService.create(1L, 1L, request(1L, 50_000, 5_000, 10));

        Page<AuctionResponse> active = auctionService.findAll(1, 10, Status.ACTIVE);

        assertThat(active.getContent()).isNotEmpty();
        assertThat(active.getContent()).allMatch(auction -> auction.getStatus() == Status.ACTIVE);
        assertThatThrownBy(() -> auctionService.findById(999_999L))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void close_rejectsBeforeDeadlineAndEndsExpiredAuctionWithoutBids() {
        Auction future = auctionRepository.save(new Auction(
            shopRepository.findById(1L).orElseThrow(),
            itemRepository.findById(1L).orElseThrow(),
            50_000,
            LocalDateTime.now().plusSeconds(600),
            5_000));

        assertThatThrownBy(() -> auctionService.close(1L, future.getId()))
            .isInstanceOf(BadRequestException.class);

        Auction expired = auctionRepository.save(new Auction(
            shopRepository.findById(1L).orElseThrow(),
            itemRepository.findById(1L).orElseThrow(),
            50_000,
            LocalDateTime.now().minusSeconds(60),
            5_000));

        AuctionResponse response = auctionService.close(1L, expired.getId());

        assertThat(response.getStatus()).isEqualTo(Status.ENDED_FAIL);
    }

    @Test
    void close_rejectsAlreadyEndedAuction() {
        Auction auction = auctionRepository.save(new Auction(
            shopRepository.findById(1L).orElseThrow(),
            itemRepository.findById(1L).orElseThrow(),
            50_000,
            LocalDateTime.now().minusSeconds(60),
            5_000));
        auctionService.close(1L, auction.getId());

        assertThatThrownBy(() -> auctionService.close(1L, auction.getId()))
            .isInstanceOf(BadRequestException.class);
    }

    private AuctionRequest request(Long itemId, int startPrice, int depositAmount, long deadlineOffsetSeconds) {
        AuctionRequest request = new AuctionRequest();
        ReflectionTestUtils.setField(request, "itemId", itemId);
        ReflectionTestUtils.setField(request, "startPrice", startPrice);
        ReflectionTestUtils.setField(request, "depositAmount", depositAmount);
        ReflectionTestUtils.setField(request, "deadline",
            LocalDateTime.now().plusSeconds(deadlineOffsetSeconds));
        return request;
    }
}
