package com.goodsbridge.board;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.auction.Auction;
import com.goodsbridge.auction.AuctionRepository;
import com.goodsbridge.event.ShopEvent;
import com.goodsbridge.event.ShopEventRepository;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.purchase.Purchase;
import com.goodsbridge.purchase.PurchaseRepository;
import com.goodsbridge.purchase.PurchaseStatus;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 리뷰(구매확정한 구매 1건당 1개)와 상품·이벤트·경매 문의(대상별 구분 저장·조회)
 * 시드(data.sql): 가게 1, 상품 여러 개
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BoardReviewInquiryIntegrationTest {

    @Autowired BoardService boardService;
    @Autowired BoardRepository boardRepository;
    @Autowired UserRepository userRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired ShopRepository shopRepository;
    @Autowired PurchaseRepository purchaseRepository;
    @Autowired ShopEventRepository shopEventRepository;
    @Autowired AuctionRepository auctionRepository;
    @Autowired MockMvc mockMvc;

    Long buyerId;
    Long otherId;
    Item itemA;
    Item itemB;

    @BeforeEach
    void setUp() {
        buyerId = user("buyer@review.test", "구매자");
        otherId = user("other@review.test", "다른사람");
        List<Item> items = itemRepository.findAll();
        itemA = items.get(0);
        itemB = items.get(1);
    }

    private Long user(String email, String nickname) {
        return userRepository.save(User.builder().email(email).password("pw").nickname(nickname)
            .loginType(LoginType.EMAIL).build()).getId();
    }

    private Purchase purchase(Long userId, Item item, PurchaseStatus status) {
        Purchase purchase = purchaseRepository.save(Purchase.builder()
            .item(item).user(userRepository.getReferenceById(userId)).paymentId("pay-" + item.getId() + "-" + userId)
            .deliveryAddress("서울").amount(1000).build());
        ReflectionTestUtils.setField(purchase, "status", status);
        purchaseRepository.flush();
        return purchase;
    }

    private BoardRequestDto review(Long itemId, Long purchaseId) {
        return BoardRequestDto.builder().title("후기").content("좋아요").boardType(BoardType.PRODUCT_REVIEW)
            .itemId(itemId).purchaseId(purchaseId).build();
    }

    private BoardRequestDto inquiry(InquiryTargetType type, Long itemId, Long targetId) {
        return BoardRequestDto.builder().title("문의").content("재고 있나요").boardType(BoardType.PRODUCT_INQUIRY)
            .targetType(type).itemId(itemId).targetId(targetId).isSecret(true).build();
    }

    // ===================== 리뷰 =====================

    @Test
    void review_requiresConfirmedPurchase() {
        Purchase paid = purchase(buyerId, itemA, PurchaseStatus.PAID);

        assertThatThrownBy(() -> boardService.createBoard(review(itemA.getId(), paid.getId()), buyerId))
            .isInstanceOf(ForbiddenException.class).hasMessageContaining("구매확정");
        // 구매를 지정하지 않아도(상품만 지정) 구매확정한 구매가 없으면 쓸 수 없다
        assertThatThrownBy(() -> boardService.createBoard(review(itemA.getId(), null), buyerId))
            .isInstanceOf(ForbiddenException.class);

        Purchase refunded = purchase(buyerId, itemB, PurchaseStatus.REFUNDED);
        assertThatThrownBy(() -> boardService.createBoard(review(itemB.getId(), refunded.getId()), buyerId))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void review_confirmedPurchase_isSavedWithItemAndPurchase() {
        Purchase confirmed = purchase(buyerId, itemA, PurchaseStatus.CONFIRMED);

        Long id = boardService.createBoard(review(itemA.getId(), confirmed.getId()), buyerId);

        Board saved = boardRepository.findById(id).orElseThrow();
        assertThat(saved.getBoardType()).isEqualTo(BoardType.PRODUCT_REVIEW);
        assertThat(saved.getItemId()).isEqualTo(itemA.getId());
        assertThat(saved.getPurchaseId()).isEqualTo(confirmed.getId());
        assertThat(saved.getIsSecret()).isFalse();
    }

    @Test
    void review_onlyOnePerPurchase() {
        Purchase confirmed = purchase(buyerId, itemA, PurchaseStatus.CONFIRMED);
        boardService.createBoard(review(itemA.getId(), confirmed.getId()), buyerId);

        assertThatThrownBy(() -> boardService.createBoard(review(itemA.getId(), confirmed.getId()), buyerId))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("이미 리뷰");
        // 상품만 지정해도 리뷰를 쓸 수 있는 구매가 더는 없다
        assertThatThrownBy(() -> boardService.createBoard(review(itemA.getId(), null), buyerId))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void review_cannotUseSomeoneElsesPurchase_orAMismatchedItem() {
        Purchase mine = purchase(buyerId, itemA, PurchaseStatus.CONFIRMED);

        assertThatThrownBy(() -> boardService.createBoard(review(itemA.getId(), mine.getId()), otherId))
            .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> boardService.createBoard(review(itemB.getId(), mine.getId()), buyerId))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> boardService.createBoard(review(itemA.getId(), 9_999_999L), buyerId))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void review_withoutPurchaseId_picksAnUnreviewedConfirmedPurchase() {
        Purchase confirmed = purchase(buyerId, itemA, PurchaseStatus.CONFIRMED);

        Long id = boardService.createBoard(review(itemA.getId(), null), buyerId);

        assertThat(boardRepository.findById(id).orElseThrow().getPurchaseId()).isEqualTo(confirmed.getId());
    }

    @Test
    void reviewList_showsOnlyThatItemsReviews() {
        Purchase a = purchase(buyerId, itemA, PurchaseStatus.CONFIRMED);
        Purchase b = purchase(buyerId, itemB, PurchaseStatus.CONFIRMED);
        boardService.createBoard(review(itemA.getId(), a.getId()), buyerId);
        boardService.createBoard(review(itemB.getId(), b.getId()), buyerId);

        Page<BoardResponseDto> forA = boardService.getBoards(BoardType.PRODUCT_REVIEW, null, itemA.getId(),
            null, null, PageRequest.of(0, 10), null);

        assertThat(forA.getContent()).hasSize(1);
        assertThat(forA.getContent().get(0).getItemId()).isEqualTo(itemA.getId());
        // 리뷰는 누구나(비로그인 포함) 볼 수 있다
        assertThat(forA.getContent().get(0).getContent()).isEqualTo("좋아요");
    }

    @Test
    void reviewEligibility_followsConfirmedAndUnreviewedPurchases() {
        assertThat(boardService.getReviewEligibility(itemA.getId(), null).reviewable()).isFalse();
        assertThat(boardService.getReviewEligibility(itemA.getId(), buyerId).reviewable()).isFalse();

        Purchase paid = purchase(buyerId, itemA, PurchaseStatus.PAID);
        assertThat(boardService.getReviewEligibility(itemA.getId(), buyerId).reviewable()).isFalse();

        ReflectionTestUtils.setField(paid, "status", PurchaseStatus.CONFIRMED);
        purchaseRepository.flush();
        ReviewEligibilityResponse eligible = boardService.getReviewEligibility(itemA.getId(), buyerId);
        assertThat(eligible.reviewable()).isTrue();
        assertThat(eligible.purchaseId()).isEqualTo(paid.getId());
        // 다른 사람에게는 해당 없다
        assertThat(boardService.getReviewEligibility(itemA.getId(), otherId).reviewable()).isFalse();

        boardService.createBoard(review(itemA.getId(), paid.getId()), buyerId);
        assertThat(boardService.getReviewEligibility(itemA.getId(), buyerId).reviewable()).isFalse();
        assertThat(boardService.getMyReviewIds(buyerId)).containsKey(paid.getId());
        assertThat(boardService.getMyReviewIds(otherId)).isEmpty();
    }

    // ===================== 문의 =====================

    @Test
    void inquiry_isStoredPerTarget() {
        Shop shop = shopRepository.findAll().get(0);
        ShopEvent event = shopEventRepository.save(new ShopEvent(shop, itemA, 10,
            LocalDateTime.now().plusDays(1), 1000, 1, LocalDate.now().plusDays(3)));
        Auction auction = auctionRepository.save(new Auction(shop, itemB, 10_000,
            LocalDateTime.now().plusHours(1), 1_000));

        Board onItem = boardRepository.findById(
            boardService.createBoard(inquiry(null, itemA.getId(), null), buyerId)).orElseThrow();
        Board onEvent = boardRepository.findById(
            boardService.createBoard(inquiry(InquiryTargetType.EVENT, null, event.getId()), buyerId)).orElseThrow();
        Board onAuction = boardRepository.findById(
            boardService.createBoard(inquiry(InquiryTargetType.AUCTION, null, auction.getId()), buyerId)).orElseThrow();

        assertThat(onItem.getTargetType()).isEqualTo(InquiryTargetType.ITEM);
        assertThat(onItem.getTargetId()).isEqualTo(itemA.getId());
        assertThat(onEvent.getTargetType()).isEqualTo(InquiryTargetType.EVENT);
        assertThat(onEvent.getTargetId()).isEqualTo(event.getId());
        assertThat(onEvent.getItemId()).isEqualTo(itemA.getId());          // 이벤트의 상품
        assertThat(onAuction.getTargetType()).isEqualTo(InquiryTargetType.AUCTION);
        assertThat(onAuction.getItemId()).isEqualTo(itemB.getId());        // 경매의 상품

        // 화면마다 자기 대상의 문의만 보인다 (같은 상품이라도 이벤트 문의는 상품 화면에 섞이지 않는다)
        PageRequest page = PageRequest.of(0, 10);
        assertThat(ids(boardService.getBoards(BoardType.PRODUCT_INQUIRY, null, itemA.getId(),
            InquiryTargetType.ITEM, null, page, buyerId))).containsExactly(onItem.getId());
        assertThat(ids(boardService.getBoards(BoardType.PRODUCT_INQUIRY, null, null,
            InquiryTargetType.EVENT, event.getId(), page, buyerId))).containsExactly(onEvent.getId());
        assertThat(ids(boardService.getBoards(BoardType.PRODUCT_INQUIRY, null, null,
            InquiryTargetType.AUCTION, auction.getId(), page, buyerId))).containsExactly(onAuction.getId());
    }

    @Test
    void inquiryList_hidesOtherPeoplesInquiries() {
        Long mine = boardService.createBoard(inquiry(null, itemA.getId(), null), buyerId);

        List<BoardResponseDto> seenByOther = boardService.getBoards(BoardType.PRODUCT_INQUIRY, null, itemA.getId(),
            InquiryTargetType.ITEM, null, PageRequest.of(0, 10), otherId).getContent();

        assertThat(seenByOther).hasSize(1);
        assertThat(seenByOther.get(0).getId()).isEqualTo(mine);
        assertThat(seenByOther.get(0).getAuthorId()).isNull();        // 내용·작성자는 숨겨진 잠긴 글
        assertThat(seenByOther.get(0).getContent()).isNull();
    }

    @Test
    void inquiry_withoutTargetType_countsAsAnItemInquiry() {
        Long id = boardService.createBoard(inquiry(null, itemA.getId(), null), buyerId);
        // 대상을 기록하기 전에 쓰인 옛 문의를 흉내 낸다
        Board old = boardRepository.findById(id).orElseThrow();
        ReflectionTestUtils.setField(old, "targetType", null);
        ReflectionTestUtils.setField(old, "targetId", null);
        boardRepository.flush();

        assertThat(ids(boardService.getBoards(BoardType.PRODUCT_INQUIRY, null, itemA.getId(),
            InquiryTargetType.ITEM, null, PageRequest.of(0, 10), buyerId))).containsExactly(id);
    }

    @Test
    void inquiry_rejectsMissingOrUnknownTargets() {
        assertThatThrownBy(() -> boardService.createBoard(inquiry(null, null, null), buyerId))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> boardService.createBoard(inquiry(InquiryTargetType.EVENT, null, null), buyerId))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> boardService.createBoard(inquiry(InquiryTargetType.EVENT, null, 9_999_999L), buyerId))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> boardService.createBoard(inquiry(InquiryTargetType.AUCTION, null, 9_999_999L), buyerId))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> boardService.getBoards(BoardType.PRODUCT_INQUIRY, null, null,
            InquiryTargetType.EVENT, null, PageRequest.of(0, 10), buyerId))
            .isInstanceOf(BadRequestException.class);
    }

    // ===================== API =====================

    @Test
    void reviewEligibilityAndMyReviewsEndpoints() throws Exception {
        Purchase confirmed = purchase(buyerId, itemA, PurchaseStatus.CONFIRMED);
        var login = authentication(new UsernamePasswordAuthenticationToken(
            buyerId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mockMvc.perform(get("/api/boards/review-eligibility").param("itemId", String.valueOf(itemA.getId())).with(login))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewable").value(true))
            .andExpect(jsonPath("$.purchaseId").value(confirmed.getId()));
        // 비로그인은 쓸 수 없다고 답한다 (403 이 아니라 false)
        mockMvc.perform(get("/api/boards/review-eligibility").param("itemId", String.valueOf(itemA.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewable").value(false));

        Long reviewId = boardService.createBoard(review(itemA.getId(), confirmed.getId()), buyerId);
        mockMvc.perform(get("/api/boards/my-reviews").with(login))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"" + confirmed.getId() + "\":" + reviewId)));
        mockMvc.perform(get("/api/boards").param("boardType", "PRODUCT_REVIEW")
                .param("itemId", String.valueOf(itemA.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1));
    }

    private List<Long> ids(Page<BoardResponseDto> page) {
        return page.getContent().stream().map(BoardResponseDto::getId).toList();
    }
}
