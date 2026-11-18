package com.goodsbridge.auction;

import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 같은 유저가 같은 경매에 여러 번 입찰해도 "처음 입찰" 조회가 예외 없이 첫 입찰을 돌려줘야 한다.
 * (BidService 는 재입찰자의 보증금을 이 조회로 찾는다. 단건 조회(@Query)로 바꾸면 입찰이 2건 이상일 때
 *  NonUniqueResultException 이 나서 세 번째 입찰부터 500 이 된다.)
 *
 * 시드(data.sql): 가게 1, 상품 5.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BidRepositoryRepeatBidderTest {

    @Autowired AuctionRepository auctionRepository;
    @Autowired BidRepository bidRepository;
    @Autowired UserRepository userRepository;
    @Autowired ShopRepository shopRepository;
    @Autowired ItemRepository itemRepository;

    @Test
    void findFirstBid_returnsEarliestBid_whenSameUserBidMultipleTimes() {
        Auction auction = auctionRepository.save(new Auction(
            shopRepository.findById(1L).orElseThrow(), itemRepository.findById(5L).orElseThrow(),
            10_000, LocalDateTime.now().plusHours(1), 1_000));
        User bidder = userRepository.save(User.builder().email("repeat@bid.test").password("pw")
            .nickname("재입찰자").loginType(LoginType.EMAIL).build());
        bidRepository.save(Bid.builder().auction(auction).user(bidder).bidAmount(12_000).depositPaymentId("dep-1").build());
        bidRepository.save(Bid.builder().auction(auction).user(bidder).bidAmount(14_000).depositPaymentId("dep-1").build());
        bidRepository.save(Bid.builder().auction(auction).user(bidder).bidAmount(16_000).depositPaymentId("dep-1").build());
        bidRepository.flush();

        var first = bidRepository.findFirstByAuctionIdAndUserIdOrderByCreatedAtAscIdAsc(auction.getId(), bidder.getId());

        assertTrue(first.isPresent());
        assertEquals(12_000, first.get().getBidAmount());
    }
}
