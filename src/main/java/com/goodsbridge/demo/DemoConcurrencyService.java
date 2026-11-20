package com.goodsbridge.demo;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.auction.Auction;
import com.goodsbridge.auction.AuctionRepository;
import com.goodsbridge.auction.Bid;
import com.goodsbridge.auction.BidRepository;
import com.goodsbridge.auction.BidRequest;
import com.goodsbridge.auction.BidService;
import com.goodsbridge.event.EventService;
import com.goodsbridge.event.ShopEvent;
import com.goodsbridge.event.ShopEventRepository;
import com.goodsbridge.event.ShopEventStatus;
import com.goodsbridge.event.dto.EventParticipationRequest;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.SaleType;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// 시연용: 여러 명이 "동시에" 요청할 때 락이 있을 때와 없을 때 결과를 비교한다 (선착순 이벤트 참가, 경매 입찰).
// 매번 임시 가게·참가자를 만들고, 끝나면 모두 지운다 (다른 데모 데이터와 섞이지 않는다).
@Service
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoConcurrencyService {

    public static final int MAX_CAPACITY = 20;
    public static final int MAX_USERS = 50;
    public static final int AUCTION_START_PRICE = 10_000;
    public static final int AUCTION_DEPOSIT = 1_000;
    // 락 없음 쪽은 "확인 → (결제 확인 같은 처리) → 저장" 사이에 이만큼 쉬어서 충돌이 눈에 보이게 한다
    static final long PROCESSING_MILLIS = 50;

    private final EventService eventService;
    private final BidService bidService;
    private final ShopEventRepository shopEventRepository;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final TransactionTemplate transactionTemplate;
    private final DemoTempData tempData;

    public record Round(int success, int fail, int recordedCount, String status, long elapsedMillis) {}

    public record Result(int capacity, int users, Round withoutLock, Round withLock) {}

    // bidCount: DB에 저장된 입찰 수 (입찰마다 보증금을 1번씩 결제했다)
    public record AuctionRound(int success, int fail, int bidCount, int currentPrice, long elapsedMillis) {}

    public record AuctionResult(int users, int bidAmount, int deposit, AuctionRound withoutLock, AuctionRound withLock) {}

    // ---------- 선착순 이벤트 참가 ----------

    public Result run(int capacity, int users) {
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new BadRequestException("정원은 1~" + MAX_CAPACITY + " 사이로 입력해 주세요.");
        }
        validateUsers(users);
        return new Result(capacity, users, eventRound(capacity, users, false), eventRound(capacity, users, true));
    }

    private Round eventRound(int capacity, int userCount, boolean locked) {
        DemoTempData.Run run = tempData.start();
        try {
            record Setup(Long eventId, List<Long> userIds) {}
            Setup setup = transactionTemplate.execute(status -> {
                User owner = tempData.user(run, "owner");
                Shop shop = tempData.shop(run, owner);
                Item item = tempData.item(run, owner, SaleType.EVENT, 10_000, "동시성 시연 상품");
                ShopEvent event = shopEventRepository.save(ShopEvent.builder()
                    .shop(shop).item(item).capacity(capacity).feeAmount(1000)
                    .deadline(LocalDateTime.now().plusDays(1)).build());
                return new Setup(event.getId(), participants(run, userCount));
            });
            List<Attempt> attempts = new ArrayList<>();
            for (int i = 0; i < userCount; i++) {
                Long userId = setup.userIds().get(i);
                String paymentId = run.paymentId("event-" + i);
                attempts.add(locked
                    ? () -> eventService.saveEventParticipation(setup.eventId(), userId,
                        tempData.request("{\"paymentId\":\"" + paymentId + "\"}", EventParticipationRequest.class))
                    : () -> joinEventWithoutLock(setup.eventId()));
            }
            Outcome outcome = runAtOnce(attempts);
            ShopEvent after = shopEventRepository.findById(setup.eventId()).orElseThrow();
            return new Round(outcome.success(), outcome.fail(), after.getCurrentCount(), after.getStatus().name(), outcome.elapsedMillis());
        } finally {
            tempData.cleanup(run);
        }
    }

    // 락 없이 실제 참가와 같은 순서로 처리한다: 읽기 → 정원 확인 → 처리 시간 → 수량 증가.
    // 여러 명이 같은 currentCount 를 읽고 모두 "자리 있음"으로 통과하는 것을 보여주기 위한 코드라 실제 서비스에서는 쓰지 않는다.
    private void joinEventWithoutLock(Long eventId) {
        transactionTemplate.executeWithoutResult(status -> {
            ShopEvent event = shopEventRepository.findById(eventId).orElseThrow();
            if (event.getCurrentCount() >= event.getCapacity()) {
                throw new BadRequestException("정원 초과로 이벤트 참가 불가능합니다");
            }
            pause();
            event.increaseCurrentCount(1);
            if (event.getCurrentCount() >= event.getCapacity()) {
                event.updateStatus(ShopEventStatus.CLOSED);
            }
        });
    }

    // ---------- 경매 입찰 ----------

    // 모두 같은 금액(시작가)으로 동시에 첫 입찰한다. 정상이라면 1명만 최고 입찰자가 되고 나머지는 "현재가보다 높아야 한다"로 거절된다.
    public AuctionResult runAuction(int users) {
        validateUsers(users);
        return new AuctionResult(users, AUCTION_START_PRICE, AUCTION_DEPOSIT, auctionRound(users, false), auctionRound(users, true));
    }

    private AuctionRound auctionRound(int userCount, boolean locked) {
        DemoTempData.Run run = tempData.start();
        try {
            record Setup(Long auctionId, List<Long> userIds) {}
            Setup setup = transactionTemplate.execute(status -> {
                User owner = tempData.user(run, "owner");
                Shop shop = tempData.shop(run, owner);
                Item item = tempData.item(run, owner, SaleType.AUCTION, AUCTION_START_PRICE, "동시 입찰 시연 상품");
                Auction auction = auctionRepository.save(Auction.builder()
                    .shop(shop).item(item).startPrice(AUCTION_START_PRICE).depositAmount(AUCTION_DEPOSIT)
                    .deadline(LocalDateTime.now().plusDays(1)).build());
                return new Setup(auction.getId(), participants(run, userCount));
            });
            List<Attempt> attempts = new ArrayList<>();
            for (int i = 0; i < userCount; i++) {
                Long userId = setup.userIds().get(i);
                String paymentId = run.paymentId("bid-" + i);
                attempts.add(locked
                    ? () -> bidService.placeBid(setup.auctionId(), userId, new BidRequest(AUCTION_START_PRICE, paymentId))
                    : () -> bidWithoutLock(setup.auctionId(), userId, paymentId));
            }
            Outcome outcome = runAtOnce(attempts);
            Auction after = auctionRepository.findById(setup.auctionId()).orElseThrow();
            int bidCount = (int) bidRepository.countByAuctionId(setup.auctionId());
            return new AuctionRound(outcome.success(), outcome.fail(), bidCount, after.getCurrentPrice(), outcome.elapsedMillis());
        } finally {
            tempData.cleanup(run);
        }
    }

    // 락 없이 실제 입찰과 같은 순서로 처리한다: 읽기 → 현재가 확인 → 처리 시간 → 보증금 결제 → 입찰 저장·현재가 갱신.
    // 여러 명이 "아직 입찰 없음"을 동시에 읽고 모두 통과하는 것을 보여주기 위한 코드라 실제 서비스에서는 쓰지 않는다.
    private void bidWithoutLock(Long auctionId, Long userId, String paymentId) {
        transactionTemplate.executeWithoutResult(status -> {
            Auction auction = auctionRepository.findById(auctionId).orElseThrow();
            boolean noBidYet = auction.getCurrentWinner() == null;
            if (noBidYet ? AUCTION_START_PRICE < auction.getCurrentPrice() : AUCTION_START_PRICE <= auction.getCurrentPrice()) {
                throw new BadRequestException("입찰가는 현재가보다 높아야 합니다.");
            }
            pause();
            String depositPaymentId = paymentService.verify(paymentId, auction.getDepositAmount(), PaymentPurpose.AUCTION_DEPOSIT, userId);
            User user = userRepository.getReferenceById(userId);
            // 현재가 갱신(UPDATE)을 먼저 내보내고 입찰을 저장한다. 입찰 INSERT 를 먼저 하면 MySQL 이 외래키 확인으로 경매 행에
            // 공유 잠금을 걸고, 이어지는 UPDATE 끼리 교착 상태가 나서 "검사를 다 같이 통과하는" 장면 대신 DB 오류로 실패한다.
            auction.updateCurrentBid(AUCTION_START_PRICE, user);
            auctionRepository.saveAndFlush(auction);
            bidRepository.save(Bid.builder().auction(auction).user(user).bidAmount(AUCTION_START_PRICE).depositPaymentId(depositPaymentId).build());
        });
    }

    // ---------- 공통 ----------

    private void validateUsers(int users) {
        if (users < 1 || users > MAX_USERS) {
            throw new BadRequestException("동시 신청 인원은 1~" + MAX_USERS + " 사이로 입력해 주세요.");
        }
    }

    private List<Long> participants(DemoTempData.Run run, int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(tempData.user(run, "u" + i).getId());
        }
        return ids;
    }

    private interface Attempt {
        void run() throws Exception;
    }

    private record Outcome(int success, int fail, long elapsedMillis) {}

    // 모든 요청을 스레드에 올려 두고, 모두 준비되면 한꺼번에 출발시킨다
    private Outcome runAtOnce(List<Attempt> attempts) {
        int count = attempts.size();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        for (Attempt attempt : attempts) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    attempt.run();
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        long begin = System.currentTimeMillis();
        try {
            ready.await();
            begin = System.currentTimeMillis();
            start.countDown();
            done.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }
        return new Outcome(success.get(), fail.get(), System.currentTimeMillis() - begin);
    }

    private static void pause() {
        try {
            Thread.sleep(PROCESSING_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
