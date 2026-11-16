package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
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

/**
 * 이슈 #12 (Auction CRUD) 통합 테스트
 *
 * data.sql 시드 기준
 *  - 유저 1: 가게 주인(USER) / 유저 2: 관리자(ADMIN)
 *  - 가게 1, 2: 둘 다 주인이 유저 1
 *  - 상품 1~3: 개인 판매(shop_id = null) / 상품 4(CLOSED), 5(ON_SALE): 가게 1 소속
 *  경매는 "해당 가게의 상품"만 등록할 수 있으므로 가게 1 소속인 5번 상품을 사용한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuctionServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long ADMIN_ID = 2L;
    private static final Long SHOP_ID = 1L;
    private static final Long OTHER_SHOP_ID = 2L;
    private static final Long SHOP_ITEM_ID = 5L;
    private static final Long PERSONAL_ITEM_ID = 3L;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UserRepository userRepository;

    // ---------- 개설 (POST /api/shops/{id}/auctions) ----------

    @Test
    void create_initializesCurrentPriceAndActiveStatus() {
        AuctionResponse response = auctionService.create(OWNER_ID, SHOP_ID,
            request(SHOP_ITEM_ID, 50_000, 5_000, 10));

        assertThat(response.getId()).isNotNull();
        assertThat(response.getCurrentPrice()).isEqualTo(50_000);
        assertThat(response.getStartPrice()).isEqualTo(50_000);
        assertThat(response.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(response.getShopId()).isEqualTo(SHOP_ID);
        assertThat(response.getItemId()).isEqualTo(SHOP_ITEM_ID);
        assertThat(response.getCurrentWinnerUserId()).isNull();
    }

    @Test
    void create_savesDepositAmountAndDeadlineFromRequest() {
        AuctionResponse response = auctionService.create(OWNER_ID, SHOP_ID,
            request(SHOP_ITEM_ID, 50_000, 7_500, 600));

        assertThat(response.getDepositAmount()).isEqualTo(7_500);
        assertThat(response.getDeadline()).isAfter(LocalDateTime.now());
        assertThat(auctionRepository.findById(response.getId()).orElseThrow().getDepositAmount())
            .isEqualTo(7_500);
    }

    @Test
    void create_rejectsNonOwner() {
        assertThatThrownBy(() -> auctionService.create(ADMIN_ID, SHOP_ID,
            request(SHOP_ITEM_ID, 50_000, 5_000, 10)))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_rejectsMissingShop() {
        assertThatThrownBy(() -> auctionService.create(OWNER_ID, 999_999L,
            request(SHOP_ITEM_ID, 50_000, 5_000, 10)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_rejectsMissingItem() {
        assertThatThrownBy(() -> auctionService.create(OWNER_ID, SHOP_ID,
            request(999_999L, 50_000, 5_000, 10)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_rejectsInvalidStartPriceDepositAndDeadline() {
        // startPrice > 0
        assertThatThrownBy(() -> auctionService.create(OWNER_ID, SHOP_ID,
            request(SHOP_ITEM_ID, 0, 5_000, 10)))
            .isInstanceOf(BadRequestException.class);
        // depositAmount < startPrice의 10%
        assertThatThrownBy(() -> auctionService.create(OWNER_ID, SHOP_ID,
            request(SHOP_ITEM_ID, 50_000, 1_000, 10)))
            .isInstanceOf(BadRequestException.class);
        // depositAmount > startPrice의 20%
        assertThatThrownBy(() -> auctionService.create(OWNER_ID, SHOP_ID,
            request(SHOP_ITEM_ID, 50_000, 15_000, 10)))
            .isInstanceOf(BadRequestException.class);
        // deadline은 미래 시각
        assertThatThrownBy(() -> auctionService.create(OWNER_ID, SHOP_ID,
            request(SHOP_ITEM_ID, 50_000, 5_000, -1)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_rejectsItemOwnedByAnotherSellerAndNotInThisShop() {
        // 3번 상품은 판매자 2, 가게 없음 -> 1번 유저(1번 가게 주인)가 올릴 수 없다.
        assertThatThrownBy(() -> auctionService.create(OWNER_ID, SHOP_ID,
            request(PERSONAL_ITEM_ID, 50_000, 5_000, 10)))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_rejectsItemBelongingToAnotherShop() {
        // 2번 가게 주인도 1번 유저지만, 5번 상품은 1번 가게 소속이라 2번 가게 경매로 등록할 수 없다.
        assertThatThrownBy(() -> auctionService.create(OWNER_ID, OTHER_SHOP_ID,
            request(SHOP_ITEM_ID, 50_000, 5_000, 10)))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_rejectsSecondActiveAuctionForSameItem() {
        auctionService.create(OWNER_ID, SHOP_ID, request(SHOP_ITEM_ID, 50_000, 5_000, 10));

        assertThatThrownBy(() -> auctionService.create(OWNER_ID, SHOP_ID,
            request(SHOP_ITEM_ID, 50_000, 5_000, 10)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_allowsNewAuctionAfterPreviousOneEnded() {
        Auction expired = saveExpiredAuction();
        auctionService.close(OWNER_ID, expired.getId());   // ENDED_FAIL

        AuctionResponse response = auctionService.create(OWNER_ID, SHOP_ID,
            request(SHOP_ITEM_ID, 50_000, 5_000, 10));

        assertThat(response.getStatus()).isEqualTo(Status.ACTIVE);
    }

    // ---------- 목록 / 상세 (GET /api/auctions, /api/auctions/{id}) ----------

    @Test
    void findAll_filtersByStatusAndFindByIdRejectsMissingAuction() {
        auctionService.create(OWNER_ID, SHOP_ID, request(SHOP_ITEM_ID, 50_000, 5_000, 10));

        Page<AuctionResponse> active = auctionService.findAll(1, 10, Status.ACTIVE);

        assertThat(active.getContent()).isNotEmpty();
        assertThat(active.getContent()).allMatch(auction -> auction.getStatus() == Status.ACTIVE);
        assertThatThrownBy(() -> auctionService.findById(999_999L))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findAll_withoutStatusReturnsAuctionsOfEveryStatus() {
        auctionService.create(OWNER_ID, SHOP_ID, request(SHOP_ITEM_ID, 50_000, 5_000, 10));
        Auction expired = saveExpiredAuction();
        auctionService.close(OWNER_ID, expired.getId());   // ENDED_FAIL

        Page<AuctionResponse> all = auctionService.findAll(1, 10, null);

        assertThat(all.getContent()).extracting(AuctionResponse::getStatus)
            .contains(Status.ACTIVE, Status.ENDED_FAIL);
    }

    @Test
    void findAll_filtersEndedFail() {
        Auction expired = saveExpiredAuction();
        auctionService.close(OWNER_ID, expired.getId());

        Page<AuctionResponse> ended = auctionService.findAll(1, 10, Status.ENDED_FAIL);

        assertThat(ended.getContent()).extracting(AuctionResponse::getId).contains(expired.getId());
        assertThat(ended.getContent()).allMatch(auction -> auction.getStatus() == Status.ENDED_FAIL);
    }

    @Test
    void findAll_normalizesInvalidPageAndSize() {
        // page는 1부터 시작(0 이하는 첫 페이지), size는 1~50으로 보정된다.
        assertThat(auctionService.findAll(0, 1_000, null).getSize()).isEqualTo(50);
        assertThat(auctionService.findAll(1, 0, null).getSize()).isEqualTo(1);
    }

    @Test
    void findById_returnsCreatedAuction() {
        AuctionResponse created = auctionService.create(OWNER_ID, SHOP_ID,
            request(SHOP_ITEM_ID, 50_000, 5_000, 10));

        AuctionResponse found = auctionService.findById(created.getId());

        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getDepositAmount()).isEqualTo(5_000);
        assertThat(found.getBalanceAmount()).isNull();   // 낙찰 전에는 잔금 정보 없음
    }

    // ---------- 마감 (PATCH /api/auctions/{id}/close) ----------

    @Test
    void close_rejectsBeforeDeadlineAndEndsExpiredAuctionWithoutBids() {
        Auction future = auctionRepository.save(new Auction(
            shopRepository.findById(SHOP_ID).orElseThrow(),
            itemRepository.findById(SHOP_ITEM_ID).orElseThrow(),
            50_000,
            LocalDateTime.now().plusSeconds(600),
            5_000));

        assertThatThrownBy(() -> auctionService.close(OWNER_ID, future.getId()))
            .isInstanceOf(BadRequestException.class);

        Auction expired = saveExpiredAuction();

        AuctionResponse response = auctionService.close(OWNER_ID, expired.getId());

        assertThat(response.getStatus()).isEqualTo(Status.ENDED_FAIL);
    }

    @Test
    void close_rejectsAlreadyEndedAuction() {
        Auction auction = saveExpiredAuction();
        auctionService.close(OWNER_ID, auction.getId());

        assertThatThrownBy(() -> auctionService.close(OWNER_ID, auction.getId()))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void close_withWinnerEndsSuccessAndSetsBalanceInfo() {
        Auction auction = saveExpiredAuction();   // 시작가 50_000, 보증금 5_000
        User bidder = userRepository.findById(ADMIN_ID).orElseThrow();
        auction.updateCurrentBid(60_000, bidder);

        AuctionResponse response = auctionService.close(OWNER_ID, auction.getId());

        assertThat(response.getStatus()).isEqualTo(Status.ENDED_SUCCESS);
        assertThat(response.getCurrentWinnerUserId()).isEqualTo(ADMIN_ID);
        assertThat(response.getBalanceAmount()).isEqualTo(55_000);   // 낙찰가 - 보증금
        assertThat(response.getBalanceDueAt()).isNotNull();
        assertThat(response.isBalancePaid()).isFalse();
    }

    @Test
    void close_allowsAdminWhoIsNotShopOwner() {
        Auction auction = saveExpiredAuction();

        AuctionResponse response = auctionService.close(ADMIN_ID, auction.getId());

        assertThat(response.getStatus()).isEqualTo(Status.ENDED_FAIL);
    }

    @Test
    void close_rejectsUserWhoIsNeitherOwnerNorAdmin() {
        User stranger = userRepository.save(User.builder()
            .email("stranger@test.com")
            .password("test-password")
            .nickname("지나가던유저")
            .loginType(LoginType.EMAIL)
            .build());
        Auction auction = saveExpiredAuction();

        assertThatThrownBy(() -> auctionService.close(stranger.getId(), auction.getId()))
            .isInstanceOf(ForbiddenException.class);
        assertThat(auction.getStatus()).isEqualTo(Status.ACTIVE);
    }

    @Test
    void close_rejectsMissingAuctionAndMissingUser() {
        assertThatThrownBy(() -> auctionService.close(OWNER_ID, 999_999L))
            .isInstanceOf(NotFoundException.class);

        Auction auction = saveExpiredAuction();
        assertThatThrownBy(() -> auctionService.close(999_999L, auction.getId()))
            .isInstanceOf(NotFoundException.class);
    }

    // ---------- helper ----------

    private Auction saveExpiredAuction() {
        return auctionRepository.save(new Auction(
            shopRepository.findById(SHOP_ID).orElseThrow(),
            itemRepository.findById(SHOP_ITEM_ID).orElseThrow(),
            50_000,
            LocalDateTime.now().minusSeconds(60),
            5_000));
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

    // ---------- 화면용 응답 / 가게별 목록 ----------

    @Test
    void create_responseCarriesItemAndShopInfoForScreens() {
        AuctionResponse response = auctionService.create(OWNER_ID, SHOP_ID,
            request(SHOP_ITEM_ID, 50_000, 5_000, 10));

        AuctionResponse detail = auctionService.findById(response.getId());
        assertThat(detail.getItemTitle()).isNotBlank();
        assertThat(detail.getShopName()).isNotBlank();
        assertThat(detail.getBidCount()).isZero();
    }

    @Test
    void findAllByShop_returnsOnlyThatShopsAuctionsAndFiltersByStatus() {
        AuctionResponse created = auctionService.create(OWNER_ID, SHOP_ID,
            request(SHOP_ITEM_ID, 50_000, 5_000, 10));

        Page<AuctionResponse> mine = auctionService.findAllByShop(1, 20, null, SHOP_ID);
        assertThat(mine.getContent()).extracting(AuctionResponse::getId).contains(created.getId());
        assertThat(mine.getContent()).allMatch(a -> a.getShopId().equals(SHOP_ID));

        Page<AuctionResponse> other = auctionService.findAllByShop(1, 20, null, OTHER_SHOP_ID);
        assertThat(other.getContent()).extracting(AuctionResponse::getId).doesNotContain(created.getId());

        Page<AuctionResponse> cancelledOnly = auctionService.findAllByShop(1, 20, Status.CANCELLED, SHOP_ID);
        assertThat(cancelledOnly.getContent()).extracting(AuctionResponse::getId).doesNotContain(created.getId());
    }
}
