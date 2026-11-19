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
import com.goodsbridge.notification.NotificationEvent;
import com.goodsbridge.purchase.Purchase;
import com.goodsbridge.purchase.PurchaseRepository;
import com.goodsbridge.purchase.PurchaseStatus;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 마이페이지 "내 문의", 판매자 "문의 관리"·"리뷰 관리"(비공개/삭제).
 * 판매자는 상품의 sellerUserId 로 정한다 (이벤트·경매는 그 상품의 판매자).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class BoardManagementIntegrationTest {

    @Autowired BoardService boardService;
    @Autowired BoardRepository boardRepository;
    @Autowired UserRepository userRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired ShopRepository shopRepository;
    @Autowired PurchaseRepository purchaseRepository;
    @Autowired ShopEventRepository shopEventRepository;
    @Autowired AuctionRepository auctionRepository;
    @Autowired MockMvc mockMvc;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired ApplicationEvents events;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    Long buyerId;
    Long otherId;
    Long sellerId;       // itemA, itemC 의 판매자
    Long otherSellerId;  // itemB 의 판매자
    Item itemA;
    Item itemB;
    Item itemC;

    @BeforeEach
    void setUp() {
        buyerId = user("buyer@manage.test", "구매자");
        otherId = user("other@manage.test", "다른사람");
        sellerId = user("seller@manage.test", "판매자");
        otherSellerId = user("seller2@manage.test", "다른판매자");
        List<Item> items = itemRepository.findAll();
        itemA = items.get(0);
        itemB = items.get(1);
        itemC = items.get(2);
        ReflectionTestUtils.setField(itemA, "sellerUserId", sellerId);
        ReflectionTestUtils.setField(itemC, "sellerUserId", sellerId);
        ReflectionTestUtils.setField(itemB, "sellerUserId", otherSellerId);
        itemRepository.flush();
    }

    private Long user(String email, String nickname) {
        return userRepository.save(User.builder().email(email).password("pw").nickname(nickname)
            .loginType(LoginType.EMAIL).build()).getId();
    }

    private Long inquiry(Long authorId, InquiryTargetType type, Long itemId, Long targetId) {
        return boardService.createBoard(BoardRequestDto.builder().title("문의").content("내용")
            .boardType(BoardType.PRODUCT_INQUIRY).targetType(type).itemId(itemId).targetId(targetId)
            .isSecret(true).build(), authorId);
    }

    private Long review(Long authorId, Item item) {
        Purchase purchase = purchaseRepository.save(Purchase.builder().item(item)
            .user(userRepository.getReferenceById(authorId)).paymentId("pay-" + item.getId() + "-" + authorId)
            .deliveryAddress("서울").amount(1000).build());
        ReflectionTestUtils.setField(purchase, "status", PurchaseStatus.CONFIRMED);
        purchaseRepository.flush();
        return boardService.createBoard(BoardRequestDto.builder().title("후기").content("좋아요")
            .boardType(BoardType.PRODUCT_REVIEW).purchaseId(purchase.getId()).build(), authorId);
    }

    private static PageRequest page() {
        return PageRequest.of(0, 20);
    }

    // ===================== 내 문의 =====================

    @Test
    void myInquiries_listOnlyMine_withTargetAndStatus() {
        Shop shop = shopRepository.findAll().get(0);
        ShopEvent event = shopEventRepository.save(new ShopEvent(shop, itemA, 10,
            LocalDateTime.now().plusDays(1), 1000, 1, LocalDate.now().plusDays(3)));
        Auction auction = auctionRepository.save(new Auction(shop, itemC, 10_000,
            LocalDateTime.now().plusHours(1), 1_000));
        Long onItem = inquiry(buyerId, null, itemA.getId(), null);
        inquiry(buyerId, InquiryTargetType.EVENT, null, event.getId());
        inquiry(buyerId, InquiryTargetType.AUCTION, null, auction.getId());
        inquiry(otherId, null, itemA.getId(), null);   // 남의 문의는 안 보인다

        assertThat(boardService.getMyInquiries(buyerId, null, page()).getTotalElements()).isEqualTo(3);
        assertThat(boardService.getMyInquiries(buyerId, InquiryTargetType.ITEM, page()).getTotalElements()).isEqualTo(1);
        assertThat(boardService.getMyInquiries(buyerId, InquiryTargetType.EVENT, page()).getTotalElements()).isEqualTo(1);
        assertThat(boardService.getMyInquiries(buyerId, InquiryTargetType.AUCTION, page()).getTotalElements()).isEqualTo(1);
        assertThat(boardService.getMyInquiries(otherId, null, page()).getTotalElements()).isEqualTo(1);

        BoardManageResponse row = boardService.getMyInquiries(buyerId, InquiryTargetType.ITEM, page()).getContent().get(0);
        assertThat(row.id()).isEqualTo(onItem);
        assertThat(row.itemTitle()).isEqualTo(itemA.getTitle());
        assertThat(row.targetType()).isEqualTo(InquiryTargetType.ITEM);
        assertThat(row.status()).isEqualTo(BoardStatus.PENDING);

        // 판매자가 답변하면 답변 완료가 된다
        ReplyRequestDto reply = new ReplyRequestDto();
        reply.setContent("재고 있어요");
        boardService.createReply(onItem, reply, sellerId);
        assertThat(boardService.getMyInquiries(buyerId, InquiryTargetType.ITEM, page()).getContent().get(0).status())
            .isEqualTo(BoardStatus.COMPLETED);

        Map<String, Long> counts = boardService.getInquiryCounts(buyerId, false);
        assertThat(counts).containsEntry("ALL", 3L).containsEntry("ITEM", 1L)
            .containsEntry("EVENT", 1L).containsEntry("AUCTION", 1L);
    }

    @Test
    void myInquiries_requireLogin() {
        assertThatThrownBy(() -> boardService.getMyInquiries(null, null, page()))
            .isInstanceOf(ForbiddenException.class);
    }

    // ===================== 판매자 문의 관리 =====================

    @Test
    void sellerInquiries_showOnlyInquiriesOnMyItems_includingEventsAndAuctions() {
        Shop shop = shopRepository.findAll().get(0);
        ShopEvent event = shopEventRepository.save(new ShopEvent(shop, itemA, 10,
            LocalDateTime.now().plusDays(1), 1000, 1, LocalDate.now().plusDays(3)));
        Auction auction = auctionRepository.save(new Auction(shop, itemC, 10_000,
            LocalDateTime.now().plusHours(1), 1_000));
        inquiry(buyerId, null, itemA.getId(), null);                              // 내 상품
        inquiry(otherId, InquiryTargetType.EVENT, null, event.getId());           // 내 상품의 이벤트
        inquiry(buyerId, InquiryTargetType.AUCTION, null, auction.getId());       // 내 상품의 경매
        inquiry(buyerId, null, itemB.getId(), null);                              // 다른 판매자의 상품

        assertThat(boardService.getSellerInquiries(sellerId, null, page()).getTotalElements()).isEqualTo(3);
        assertThat(boardService.getSellerInquiries(sellerId, InquiryTargetType.ITEM, page()).getTotalElements()).isEqualTo(1);
        assertThat(boardService.getSellerInquiries(sellerId, InquiryTargetType.EVENT, page()).getTotalElements()).isEqualTo(1);
        assertThat(boardService.getSellerInquiries(sellerId, InquiryTargetType.AUCTION, page()).getTotalElements()).isEqualTo(1);
        assertThat(boardService.getSellerInquiries(otherSellerId, null, page()).getTotalElements()).isEqualTo(1);
        // 판매자가 아닌 사람에게는 아무것도 없다
        assertThat(boardService.getSellerInquiries(buyerId, null, page()).getTotalElements()).isZero();

        assertThat(boardService.getInquiryCounts(sellerId, true))
            .containsEntry("ALL", 3L).containsEntry("ITEM", 1L).containsEntry("EVENT", 1L).containsEntry("AUCTION", 1L);
    }

    // ===================== 판매자 리뷰 관리 =====================

    @Test
    void sellerReviews_listReviewsOnMyItems_only() {
        review(buyerId, itemA);
        review(otherId, itemB);   // 다른 판매자의 상품

        Page<BoardManageResponse> mine = boardService.getSellerReviews(sellerId, page());
        assertThat(mine.getTotalElements()).isEqualTo(1);
        assertThat(mine.getContent().get(0).itemId()).isEqualTo(itemA.getId());
        assertThat(mine.getContent().get(0).authorNickname()).isEqualTo("구매자");
        assertThat(mine.getContent().get(0).hiddenBySeller()).isFalse();
        assertThat(boardService.getSellerReviews(buyerId, page()).getTotalElements()).isZero();
    }

    @Test
    void hidingAReview_removesItFromOthers_butAuthorAndSellerStillSeeIt() {
        Long reviewId = review(buyerId, itemA);

        boardService.setReviewHidden(reviewId, true, sellerId);

        // 상품 상세의 리뷰 목록: 다른 사람(비로그인 포함)에게는 사라지고 작성자 본인에게는 남는다
        assertThat(boardService.getBoards(BoardType.PRODUCT_REVIEW, null, itemA.getId(), null, null, page(), otherId)
            .getTotalElements()).isZero();
        assertThat(boardService.getBoards(BoardType.PRODUCT_REVIEW, null, itemA.getId(), null, null, page(), null)
            .getTotalElements()).isZero();
        Page<BoardResponseDto> asAuthor = boardService.getBoards(BoardType.PRODUCT_REVIEW, null, itemA.getId(),
            null, null, page(), buyerId);
        assertThat(asAuthor.getTotalElements()).isEqualTo(1);
        assertThat(asAuthor.getContent().get(0).getHiddenBySeller()).isTrue();   // "판매자가 비공개로 바꿈" 표시용

        // 상세: 작성자와 판매자는 열 수 있고 다른 사람은 열 수 없다
        assertThat(boardService.getBoard(reviewId, buyerId).getHiddenBySeller()).isTrue();
        assertThat(boardService.getBoard(reviewId, sellerId).getId()).isEqualTo(reviewId);
        assertThatThrownBy(() -> boardService.getBoard(reviewId, otherId)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> boardService.getBoard(reviewId, null)).isInstanceOf(NotFoundException.class);

        // 판매자의 리뷰 관리 목록에는 계속 보인다 (비공개 표시와 함께)
        assertThat(boardService.getSellerReviews(sellerId, page()).getContent().get(0).hiddenBySeller()).isTrue();

        // 다시 공개하면 모두에게 보인다
        boardService.setReviewHidden(reviewId, false, sellerId);
        assertThat(boardService.getBoards(BoardType.PRODUCT_REVIEW, null, itemA.getId(), null, null, page(), otherId)
            .getTotalElements()).isEqualTo(1);
        assertThat(boardService.getBoard(reviewId, otherId).getHiddenBySeller()).isFalse();
    }

    @Test
    void onlyTheItemsSellerCanHide_andOnlyReviews() {
        Long reviewId = review(buyerId, itemA);
        Long inquiryId = inquiry(buyerId, null, itemA.getId(), null);

        assertThatThrownBy(() -> boardService.setReviewHidden(reviewId, true, otherSellerId))
            .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> boardService.setReviewHidden(reviewId, true, buyerId))
            .isInstanceOf(ForbiddenException.class);   // 작성자 본인도 비공개 처리는 판매자 기능이다
        assertThatThrownBy(() -> boardService.setReviewHidden(inquiryId, true, sellerId))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> boardService.setReviewHidden(9_999_999L, true, sellerId))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> boardService.setReviewHidden(reviewId, true, null))
            .isInstanceOf(ForbiddenException.class);
        assertThat(boardRepository.findById(reviewId).orElseThrow().isHiddenBySeller()).isFalse();
    }

    @Test
    void sellerCanDeleteReviewsOnOwnItems_butNothingElse() {
        Long reviewId = review(buyerId, itemA);
        Long otherSellersReview = review(otherId, itemB);
        Long inquiryId = inquiry(buyerId, null, itemA.getId(), null);

        // 다른 판매자는 못 지운다
        assertThatThrownBy(() -> boardService.deleteBoard(reviewId, otherSellerId)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> boardService.deleteBoard(otherSellersReview, sellerId)).isInstanceOf(ForbiddenException.class);
        // 문의는 판매자가 지울 수 없다 (작성자만)
        assertThatThrownBy(() -> boardService.deleteBoard(inquiryId, sellerId)).isInstanceOf(ForbiddenException.class);

        boardService.deleteBoard(reviewId, sellerId);
        assertThat(boardRepository.findById(reviewId)).isEmpty();
        // 작성자 본인은 계속 지울 수 있다
        boardService.deleteBoard(inquiryId, buyerId);
        assertThat(boardRepository.findById(inquiryId)).isEmpty();
    }

    // ===================== API =====================

    @Test
    void endpoints_work_forTheRightPeople() throws Exception {
        Long reviewId = review(buyerId, itemA);
        inquiry(buyerId, null, itemA.getId(), null);
        RequestPostProcessor seller = login(sellerId);
        RequestPostProcessor buyer = login(buyerId);

        mockMvc.perform(get("/api/boards/my-inquiries").with(buyer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].itemTitle").value(itemA.getTitle()));
        mockMvc.perform(get("/api/boards/seller/inquiries").param("targetType", "ITEM").with(seller))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/boards/seller/reviews").with(seller))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(reviewId));
        mockMvc.perform(get("/api/boards/inquiry-counts").param("scope", "seller").with(seller))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ALL").value(1)).andExpect(jsonPath("$.EVENT").value(0));
        mockMvc.perform(get("/api/boards/my-inquiries"))
            .andExpect(status().isForbidden());   // 비로그인

        mockMvc.perform(patch("/api/boards/{id}/hidden", reviewId).with(seller)
                .contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/boards/{id}/hidden", reviewId).with(buyer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":false}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/boards/{id}/hidden", reviewId).with(seller)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/boards/{id}", reviewId).with(seller))
            .andExpect(status().isNoContent());
    }

    @Test
    void customerServicePosts_areAlwaysSecret_andOnlyAdminSeesTheSupportInbox() throws Exception {
        // 작성자가 비밀글이 아니라고 보내도 고객센터 글은 비공개로 저장된다
        Board cs = boardRepository.save(Board.builder().title("고객센터 문의").content("내용").authorId(buyerId)
            .boardType(BoardType.CUSTOMER_SERVICE).itemId(null).isSecret(false).build());
        assertThat(cs.getIsSecret()).isTrue();
        cs.update("고객센터 문의", "수정", false);
        assertThat(cs.getIsSecret()).isTrue();

        RequestPostProcessor admin = authentication(new UsernamePasswordAuthenticationToken(
            otherId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        mockMvc.perform(get("/api/boards/admin/support").with(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(cs.getId()))
            .andExpect(jsonPath("$.content[0].status").value("PENDING"));
        mockMvc.perform(get("/api/boards/admin/support").param("status", "COMPLETED").with(admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/boards/admin/support/counts").with(admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ALL").value(1)).andExpect(jsonPath("$.PENDING").value(1));
        // 일반 사용자는 볼 수 없다
        mockMvc.perform(get("/api/boards/admin/support").with(login(buyerId)))
            .andExpect(status().isForbidden());
    }

    @Test
    void replies_canBeEditedAndDeletedOnlyByTheirAuthor() throws Exception {
        Board cs = boardRepository.save(Board.builder().title("문의").content("내용").authorId(buyerId)
            .boardType(BoardType.CUSTOMER_SERVICE).itemId(null).isSecret(true).build());
        RequestPostProcessor admin = authentication(new UsernamePasswordAuthenticationToken(
            otherId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        Long replyId = Long.valueOf(mockMvc.perform(
                post("/api/boards/{id}/replies", cs.getId())
                    .with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"처음 답변\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        // 작성자(관리자)만 수정할 수 있다
        mockMvc.perform(put("/api/replies/{id}", replyId)
                .with(login(buyerId)).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"가로채기\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/replies/{id}", replyId)
                .with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  고친 답변  \"}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/replies/{id}", replyId)
                .with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/boards/{id}", cs.getId()).with(admin))
            .andExpect(jsonPath("$.replies[0].content").value("고친 답변"));

        // 삭제도 작성자만, 마지막 답변을 지우면 답변 대기로 돌아간다
        mockMvc.perform(delete("/api/replies/{id}", replyId).with(login(buyerId)))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/replies/{id}", replyId).with(admin))
            .andExpect(status().isNoContent());
        em.flush(); // 상태 변경(답변 대기)을 먼저 반영한 뒤
        em.clear(); // 벌크 삭제 뒤라 같은 트랜잭션의 캐시를 비우고 다시 읽는다
        mockMvc.perform(get("/api/boards/{id}", cs.getId()).with(admin))
            .andExpect(jsonPath("$.replies.length()").value(0))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ===================== 답변 상태 / 추가 질문 / 리뷰 댓글 =====================

    private Long reply(Long boardId, Long authorId, String content) {
        return boardService.createReply(boardId, contentDto(content), authorId);
    }

    private static ReplyRequestDto contentDto(String content) {
        ReplyRequestDto dto = new ReplyRequestDto();
        dto.setContent(content);
        return dto;
    }

    private BoardStatus statusOf(Long boardId) {
        return boardRepository.findById(boardId).orElseThrow().getStatus();
    }

    @Test
    void deletingReply_recomputesStatusFromLatestRemainingReply() {
        Long id = inquiry(buyerId, null, itemA.getId(), null);
        reply(id, sellerId, "첫 답변");
        Long followUp = reply(id, buyerId, "추가 질문");
        assertThat(statusOf(id)).isEqualTo(BoardStatus.PENDING);   // 추가 질문 → 답변 대기
        Long second = reply(id, sellerId, "두 번째 답변");
        assertThat(statusOf(id)).isEqualTo(BoardStatus.COMPLETED);

        // 판매자의 마지막 답변을 지우면 추가 질문이 남아 답변 대기
        boardService.deleteReply(second, sellerId);
        assertThat(statusOf(id)).isEqualTo(BoardStatus.PENDING);

        // 추가 질문까지 지우면 남은 마지막 댓글이 판매자 답변이라 답변 완료
        boardService.deleteReply(followUp, buyerId);
        assertThat(statusOf(id)).isEqualTo(BoardStatus.COMPLETED);
    }

    @Test
    void followUpQuestion_notifiesSellerOrAdmins() {
        Long id = inquiry(buyerId, null, itemA.getId(), null);
        reply(id, sellerId, "답변");
        events.clear();
        reply(id, buyerId, "추가 질문");
        assertThat(events.stream(NotificationEvent.class).map(NotificationEvent::userId)).containsExactly(sellerId);

        // 고객센터: 관리자 모두에게 알린다
        jdbcTemplate.update("update user_tb set role = 'ADMIN' where id = ?", otherId);
        Long cs = boardRepository.save(Board.builder().title("문의").content("내용").authorId(buyerId)
            .boardType(BoardType.CUSTOMER_SERVICE).isSecret(true).build()).getId();
        events.clear();
        reply(cs, buyerId, "추가 질문");
        assertThat(events.stream(NotificationEvent.class).map(NotificationEvent::userId))
            .containsExactlyInAnyOrderElementsOf(userRepository.findIdsByRole(Role.ADMIN))
            .contains(otherId);
        assertThat(statusOf(cs)).isEqualTo(BoardStatus.PENDING);
    }

    @Test
    void newInquiryAndReview_notifyTheSellerOrAdmins() {
        events.clear();
        Long inquiryId = inquiry(buyerId, null, itemA.getId(), null);
        assertThat(events.stream(NotificationEvent.class).filter(e -> e.referenceId().equals(inquiryId)))
            .extracting(NotificationEvent::userId, NotificationEvent::type)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(sellerId, com.goodsbridge.notification.Notification.Type.BOARD_INQUIRY));

        events.clear();
        Long reviewId = review(buyerId, itemA);
        assertThat(events.stream(NotificationEvent.class).filter(e -> e.referenceId().equals(reviewId)))
            .extracting(NotificationEvent::userId, NotificationEvent::type)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(sellerId, com.goodsbridge.notification.Notification.Type.BOARD_REVIEW));

        // 고객센터 문의는 관리자 전원에게
        jdbcTemplate.update("update user_tb set role = 'ADMIN' where id = ?", otherId);
        events.clear();
        Long csId = boardService.createBoard(BoardRequestDto.builder().title("고객센터").content("내용")
            .boardType(BoardType.CUSTOMER_SERVICE).build(), buyerId);
        assertThat(events.stream(NotificationEvent.class).filter(e -> e.referenceId().equals(csId)).map(NotificationEvent::userId))
            .containsExactlyInAnyOrderElementsOf(userRepository.findIdsByRole(Role.ADMIN))
            .contains(otherId);

        // 판매자가 자기 상품에 문의를 남기면 자기에게는 알리지 않는다
        events.clear();
        inquiry(sellerId, null, itemA.getId(), null);
        assertThat(events.stream(NotificationEvent.class)).isEmpty();
    }

    @Test
    void reviewReplies_onlyBySellerOfTheItem() {
        Long id = review(buyerId, itemA);
        assertThatThrownBy(() -> reply(id, buyerId, "작성자 댓글")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> reply(id, otherId, "남의 댓글")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> reply(id, otherSellerId, "다른 판매자")).isInstanceOf(ForbiddenException.class);
        assertThat(reply(id, sellerId, "감사합니다")).isNotNull();
    }

    @Test
    void inquiryLists_filterByStatusOnServer() throws Exception {
        Long answered = inquiry(buyerId, null, itemA.getId(), null);
        reply(answered, sellerId, "답변");
        inquiry(buyerId, null, itemC.getId(), null);

        assertThat(boardService.getMyInquiries(buyerId, null, BoardStatus.PENDING, page()).getTotalElements()).isEqualTo(1);
        assertThat(boardService.getMyInquiries(buyerId, InquiryTargetType.ITEM, BoardStatus.COMPLETED, page())
            .getContent()).extracting(BoardManageResponse::id).containsExactly(answered);
        assertThat(boardService.getSellerInquiries(sellerId, null, BoardStatus.COMPLETED, page()).getTotalElements()).isEqualTo(1);
        assertThat(boardService.getSellerInquiries(sellerId, null, null, page()).getTotalElements()).isEqualTo(2);

        mockMvc.perform(get("/api/boards/seller/inquiries").param("status", "PENDING").with(login(sellerId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    private RequestPostProcessor login(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
