package com.goodsbridge.board;

import com.goodsbridge.auction.Auction;
import com.goodsbridge.auction.AuctionRepository;
import com.goodsbridge.event.ShopEvent;
import com.goodsbridge.event.ShopEventRepository;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상세 화면의 문의·리뷰 영역:
 * - 상품 상세: 그 상품의 리뷰와 문의
 * - 이벤트·경매 상세: 그 이벤트·경매의 문의
 * - 내 구매 내역: 구매확정한 구매의 "리뷰 쓰기"
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InquiryReviewViewTest {

    @Autowired WebApplicationContext context;
    @Autowired ItemRepository itemRepository;
    @Autowired ShopRepository shopRepository;
    @Autowired ShopEventRepository shopEventRepository;
    @Autowired AuctionRepository auctionRepository;
    @Autowired UserRepository userRepository;
    @Autowired BoardRepository boardRepository;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    private Item firstItem() {
        return itemRepository.findAll().get(0);
    }

    @Test
    void itemDetail_showsThatItemsReviewsAndInquiries() throws Exception {
        Item item = firstItem();

        mockMvc.perform(get("/test/item/" + item.getId()))
            .andExpect(status().isOk())
            // 리뷰 영역: 이 상품의 리뷰만 불러온다
            .andExpect(content().string(containsString("id=\"rv-root\"")))
            .andExpect(content().string(containsString("const ITEM_ID = " + item.getId() + ";")))
            .andExpect(content().string(containsString("boardType: 'PRODUCT_REVIEW', itemId: ITEM_ID")))
            .andExpect(content().string(containsString("/api/boards/review-eligibility?itemId=")))
            .andExpect(content().string(containsString("id=\"rv-modal\"")))
            // 문의 영역: 상품에 대한 문의
            .andExpect(content().string(containsString("id=\"iq-root\"")))
            .andExpect(content().string(containsString("const TARGET_TYPE = 'ITEM';")))
            .andExpect(content().string(containsString("const TARGET_ID = " + item.getId() + ";")));
    }

    @Test
    void eventDetail_showsOnlyTheInquiryArea_forThatEvent() throws Exception {
        Shop shop = shopRepository.findAll().get(0);
        ShopEvent event = shopEventRepository.save(new ShopEvent(shop, firstItem(), 10,
            LocalDateTime.now().plusDays(1), 1000, 1, LocalDate.now().plusDays(3)));

        mockMvc.perform(get("/test/event/" + event.getId()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"iq-root\"")))
            .andExpect(content().string(containsString("const TARGET_TYPE = 'EVENT';")))
            .andExpect(content().string(containsString("const TARGET_ID = " + event.getId() + ";")))
            // 리뷰는 상품 상세에서만 보인다
            .andExpect(content().string(not(containsString("id=\"rv-root\""))));
    }

    @Test
    void auctionDetail_showsOnlyTheInquiryArea_forThatAuction() throws Exception {
        Shop shop = shopRepository.findAll().get(0);
        Auction auction = auctionRepository.save(new Auction(shop, firstItem(), 10_000,
            LocalDateTime.now().plusHours(1), 1_000));

        mockMvc.perform(get("/test/auction/" + auction.getId()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"iq-root\"")))
            .andExpect(content().string(containsString("const TARGET_TYPE = 'AUCTION';")))
            .andExpect(content().string(containsString("const TARGET_ID = " + auction.getId() + ";")))
            .andExpect(content().string(not(containsString("id=\"rv-root\""))));
    }

    @Test
    void myPurchases_hasTheReviewWriterForConfirmedPurchases() throws Exception {
        mockMvc.perform(get("/my/purchases"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"rv-modal\"")))
            .andExpect(content().string(containsString("/api/boards/my-reviews")))
            .andExpect(content().string(containsString("p.purchaseStatus === 'CONFIRMED' ? (myReviews[p.purchaseId]")))
            .andExpect(content().string(containsString("window.openReviewModal(")));
    }

    @Test
    void boardDetail_goesBackToWhereTheWritingCameFrom() throws Exception {
        Shop shop = shopRepository.findAll().get(0);
        ShopEvent event = shopEventRepository.save(new ShopEvent(shop, firstItem(), 10,
            LocalDateTime.now().plusDays(1), 1000, 1, LocalDate.now().plusDays(3)));
        Long authorId = userRepository.save(User.builder().email("back@link.test").password("pw")
            .nickname("뒤로가기").loginType(LoginType.EMAIL).build()).getId();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(authorId, null, List.of()));

        Board eventInquiry = boardRepository.save(Board.builder().title("이벤트 문의").content("내용").authorId(authorId)
            .boardType(BoardType.PRODUCT_INQUIRY).itemId(firstItem().getId()).isSecret(true)
            .targetType(InquiryTargetType.EVENT).targetId(event.getId()).build());
        Board itemInquiry = boardRepository.save(Board.builder().title("상품 문의").content("내용").authorId(authorId)
            .boardType(BoardType.PRODUCT_INQUIRY).itemId(firstItem().getId()).isSecret(true).build());

        // 이벤트에 남긴 문의는 그 이벤트 화면으로, 상품 문의는 그 상품 화면으로 돌아간다
        mockMvc.perform(get("/test/board/" + eventInquiry.getId()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("href=\"/test/event/" + event.getId() + "\" class=\"back-link\"")));
        mockMvc.perform(get("/test/board/" + itemInquiry.getId()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("href=\"/test/item/" + firstItem().getId() + "\" class=\"back-link\"")));
    }
}
