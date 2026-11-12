package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ★ 이슈 #20 체크리스트: "동시성 테스트 작성 — 동시에 입찰 여러 건을 보내서 최고가만 정확히 반영되는지 확인"
 *   (이 테스트는 삭제하지 않고 남기기)
 *
 * 실제 DB(H2)의 PESSIMISTIC_WRITE 락(AuctionRepository.findByIdLock)이 동작하는지 보려는 것이라
 *  - @Transactional 을 붙이지 않는다. (붙이면 모든 스레드가 한 트랜잭션/커넥션을 공유하게 돼 동시성이 사라진다)
 *  - 스레드마다 TransactionTemplate 으로 "요청 1건 = 트랜잭션 1건" 을 흉내 낸다.
 *  - 결제 검증(PortOne)은 mock 으로 대체한다. BidService 는 스프링 빈 대신 직접 생성해서 쓴다
 *    (@MockitoBean 을 쓰면 별도 스프링 컨텍스트가 하나 더 떠서 같은 H2 DB 를 건드리기 때문).
 *  - 테스트가 커밋한 데이터(경매/입찰/유저)는 @AfterEach 에서 직접 지운다.
 *
 * 시드(data.sql): 가게 1(주인=유저 1), 상품 5(가게 1 소속). 입찰자는 테스트에서 새로 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
class BidConcurrencyTest {

    private static final Long SHOP_ID = 1L;
    private static final Long SHOP_ITEM_ID = 5L;
    private static final int START_PRICE = 10_000;
    private static final int DEPOSIT = 1_000;
    private static final int THREADS = 8;   // Hikari 기본 풀(10)보다 작게

    private enum Outcome { ACCEPTED, REJECTED }

    @Autowired AuctionRepository auctionRepository;
    @Autowired BidRepository bidRepository;
    @Autowired UserRepository userRepository;
    @Autowired ShopRepository shopRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private PaymentService paymentService;
    private BidService bidService;
    private Long auctionId;
    private List<Long> bidderIds;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);

        // 결제 검증은 항상 성공하고, 받은 paymentId 를 그대로 돌려준다
        paymentService = mock(PaymentService.class);
        when(paymentService.verify(anyString(), anyInt(), any(), anyLong()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        bidService = new BidService(bidRepository, auctionRepository, userRepository, paymentService);

        auctionId = tx.execute(status -> auctionRepository.save(new Auction(
            shopRepository.findById(SHOP_ID).orElseThrow(),
            itemRepository.findById(SHOP_ITEM_ID).orElseThrow(),
            START_PRICE,
            LocalDateTime.now().plusHours(1),   // 하드 클로징 경계와 무관하게 동시 입찰만 검증
            DEPOSIT)).getId());

        bidderIds = tx.execute(status -> {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                ids.add(userRepository.save(User.builder()
                    .email("bidder" + i + "@concurrency.test")
                    .password("test-password")
                    .nickname("동시입찰자" + i)
                    .loginType(LoginType.EMAIL)
                    .build()).getId());
            }
            return ids;
        });
    }

    @AfterEach
    void tearDown() {
        // FK 순서: 입찰 -> 경매 -> 유저
        tx.executeWithoutResult(status -> {
            bidsOf(auctionId).forEach(bidRepository::delete);
            auctionRepository.deleteById(auctionId);
            userRepository.deleteAllById(bidderIds);
        });
    }

    // ===================== 시나리오 =====================

    @Test
    void sameAmountFromManyUsers_onlyOneBidIsAccepted() throws Exception {
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (Long userId : bidderIds) {
            tasks.add(() -> bid(userId, 20_000));
        }

        List<Outcome> outcomes = runConcurrently(tasks);

        assertEquals(1, count(outcomes, Outcome.ACCEPTED), "같은 금액이면 먼저 락을 잡은 1건만 성공해야 한다");
        assertEquals(THREADS - 1, count(outcomes, Outcome.REJECTED));

        tx.executeWithoutResult(status -> {
            Auction auction = auctionRepository.findById(auctionId).orElseThrow();
            List<Bid> bids = bidsOf(auctionId);

            assertEquals(20_000, auction.getCurrentPrice());
            assertEquals(1, bids.size());
            assertEquals(bids.get(0).getUser().getId(), auction.getCurrentWinner().getId());
        });
    }

    @Test
    void differentAmountsFromManyUsers_highestBidWinsAndSavedBidsAreStrictlyIncreasing() throws Exception {
        int highest = 11_000 + (THREADS - 1) * 1_000;   // 18_000
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Long userId = bidderIds.get(i);
            int amount = 11_000 + i * 1_000;
            tasks.add(() -> bid(userId, amount));
        }

        List<Outcome> outcomes = runConcurrently(tasks);

        long accepted = count(outcomes, Outcome.ACCEPTED);
        assertEquals(THREADS, outcomes.size());
        assertTrue(accepted >= 1, "최고가 입찰은 항상 성공해야 한다");

        tx.executeWithoutResult(status -> {
            Auction auction = auctionRepository.findById(auctionId).orElseThrow();
            List<Bid> bids = bidsOf(auctionId);   // id(=처리 순서) 오름차순

            // 1) 현재가/낙찰 후보는 가장 높은 입찰 하나로 정확히 반영된다
            assertEquals(highest, auction.getCurrentPrice());
            assertEquals(bidderIds.get(THREADS - 1), auction.getCurrentWinner().getId());
            assertEquals("deposit-" + bidderIds.get(THREADS - 1), auction.getWinningPaymentId());

            // 2) 성공 응답을 받은 입찰 수 == 실제 저장된 입찰 수 (유실/중복 저장 없음)
            assertEquals(accepted, bids.size());

            // 3) 락이 직렬화했다면 저장된 입찰은 처리 순서대로 금액이 항상 증가한다
            for (int i = 1; i < bids.size(); i++) {
                assertTrue(bids.get(i).getBidAmount() > bids.get(i - 1).getBidAmount(),
                    "현재가보다 낮거나 같은 입찰이 저장되었습니다: " + bids.get(i - 1).getBidAmount()
                        + " -> " + bids.get(i).getBidAmount());
            }
            assertEquals(highest, bids.get(bids.size() - 1).getBidAmount());
        });
    }

    @Test
    void sameUserBiddingConcurrently_verifiesDepositOnlyOnce() throws Exception {
        Long userId = bidderIds.get(0);
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int amount : new int[]{11_000, 12_000, 13_000}) {
            tasks.add(() -> bid(userId, amount));
        }

        runConcurrently(tasks);

        // 최초 입찰에만 보증금 검증, 이후 입찰은 검증 스킵 (이슈 #20 유효성 검사)
        verify(paymentService, times(1)).verify(anyString(), anyInt(), any(), anyLong());

        tx.executeWithoutResult(status -> {
            Auction auction = auctionRepository.findById(auctionId).orElseThrow();
            List<Bid> bids = bidsOf(auctionId);

            assertEquals(13_000, auction.getCurrentPrice());
            // 같은 유저의 모든 입찰은 같은 보증금 결제 id 를 공유한다
            assertTrue(bids.stream().allMatch(b -> ("deposit-" + userId).equals(b.getDepositPaymentId())));
        });
    }

    // ===================== helper =====================

    /** 요청 1건 = 트랜잭션 1건. 가격이 낮아 거부된 경우(BadRequestException)만 REJECTED 로 본다. */
    private Outcome bid(Long userId, int amount) {
        try {
            tx.execute(status ->
                bidService.placeBid(auctionId, userId, new BidRequest(amount, "deposit-" + userId)));
            return Outcome.ACCEPTED;
        } catch (BadRequestException e) {
            return Outcome.REJECTED;
        }
    }

    /** 모든 스레드를 준비시킨 뒤 동시에 출발시킨다. 예상 밖 예외(락 타임아웃 등)는 그대로 테스트 실패가 된다. */
    private List<Outcome> runConcurrently(List<Callable<Outcome>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Callable<Outcome> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "스레드가 준비되지 않았습니다.");
            start.countDown();

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private long count(List<Outcome> outcomes, Outcome target) {
        return outcomes.stream().filter(o -> o == target).count();
    }

    /** 트랜잭션 안에서 호출할 것 (지연 로딩). id 오름차순 = 락을 잡고 처리된 순서 */
    private List<Bid> bidsOf(Long auctionId) {
        return bidRepository.findAll().stream()
            .filter(b -> b.getAuction().getId().equals(auctionId))
            .sorted(Comparator.comparing(Bid::getId))
            .toList();
    }
}
