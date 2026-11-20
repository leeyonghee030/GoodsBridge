package com.goodsbridge.demo;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.exchange.ExchangeItem;
import com.goodsbridge.exchange.ExchangeItemRepository;
import com.goodsbridge.exchange.ExchangeRequest;
import com.goodsbridge.exchange.ExchangeRequestRepository;
import com.goodsbridge.exchange.ExchangeService;
import com.goodsbridge.exchange.dto.ExchangeAcceptRequest;
import com.goodsbridge.exchange.dto.ExchangeCheckInRequest;
import com.goodsbridge.exchange.dto.ExchangeRequestCreateRequest;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.SaleType;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

// 시연용: 교환 노쇼 정산 3케이스를 실제 교환 흐름 그대로 돌린다.
// 교환 제안(B 보증금) → 수락(A 보증금) → 가게 출석 체크 → 방문 기한 지남 → 기한 스케줄러가 판정·정산.
// 케이스마다 임시 사용자·가게·상품을 만들고, 결과를 읽은 뒤 모두 지운다.
@Service
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoSettlementService {

    public static final int MAX_PRICE = 1_000_000;

    private final ExchangeService exchangeService;
    private final ExchangeItemRepository exchangeItemRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final DemoTempData tempData;

    // expected*: 기획서 3.3절 규칙으로 계산한 기대값 (실제 정산 결과와 비교용)
    public record Person(boolean showed, int deposit, int refund, int expectedRefund, int shopFee) {}

    public record Case(String name, String status, Person a, Person b, int shopTotal, boolean matchesSpec) {}

    public record Result(int priceA, int priceB, List<Case> cases) {}

    public Result run(int priceA, int priceB) {
        if (priceA < 1 || priceA > MAX_PRICE || priceB < 1 || priceB > MAX_PRICE) {
            throw new BadRequestException("상품 가격은 1~" + String.format("%,d", MAX_PRICE) + "원 사이로 입력해 주세요.");
        }
        return new Result(priceA, priceB, List.of(
            runCase("둘 다 옴 (교환 성공)", priceA, priceB, true, true),
            runCase("한쪽만 노쇼 (B가 안 옴)", priceA, priceB, true, false),
            runCase("둘 다 노쇼", priceA, priceB, false, false)));
    }

    private Case runCase(String name, int priceA, int priceB, boolean aShowed, boolean bShowed) {
        DemoTempData.Run run = tempData.start();
        try {
            record Setup(Long userA, Long userB, Long owner, Long exchangeItemId, Long itemBId) {}
            Setup s = transactionTemplate.execute(status -> {
                User a = tempData.user(run, "A");
                User b = tempData.user(run, "B");
                User owner = tempData.user(run, "owner");
                Shop shop = tempData.shop(run, owner);
                Item itemA = tempData.item(run, a, SaleType.NORMAL, priceA, "A의 굿즈");
                Item itemB = tempData.item(run, b, SaleType.NORMAL, priceB, "B의 굿즈");
                ExchangeItem post = exchangeItemRepository.save(ExchangeItem.builder()
                    .user(a).item(itemA).shop(shop).title("노쇼 정산 시연").wantDescription("교환 원해요").build());
                return new Setup(a.getId(), b.getId(), owner.getId(), post.getId(), itemB.getId());
            });

            // 실제 서비스 흐름 그대로: B 제안(B 보증금) → A 수락(A 보증금) → 가게 출석 체크
            Long requestId = exchangeService.proposeExchange(s.exchangeItemId(), tempData.request(
                "{\"itemBId\":" + s.itemBId() + ",\"message\":\"교환해요\",\"paymentId\":\"" + run.paymentId("B") + "\"}",
                ExchangeRequestCreateRequest.class), s.userB()).getId();
            exchangeService.accept(requestId, s.userA(),
                tempData.request("{\"paymentId\":\"" + run.paymentId("A") + "\"}", ExchangeAcceptRequest.class));
            exchangeService.checkIn(requestId, s.owner(), tempData.request(
                "{\"userAShowed\":" + aShowed + ",\"userBShowed\":" + bShowed + "}", ExchangeCheckInRequest.class));

            // 방문 기한을 지난 것으로 당기고 기한 스케줄러를 바로 돌린다 (DB 시간대 차이와 무관하게 하루 전으로)
            jdbcTemplate.update("update exchange_request_tb set deadline = ? where id = ?", LocalDateTime.now().minusDays(1), requestId);
            exchangeService.autoCloseExchangeEvent();

            ExchangeRequest e = exchangeRequestRepository.findById(requestId).orElseThrow();
            Person a = person(aShowed, bShowed, e.getDepositAmountA(), e.getPaymentIdA());
            Person b = person(bShowed, aShowed, e.getDepositAmountB(), e.getPaymentIdB());
            boolean matches = a.refund() == a.expectedRefund() && b.refund() == b.expectedRefund()
                && a.shopFee() + b.shopFee() == shopTotal(e);
            return new Case(name, e.getStatus().getDisplayName(), a, b, shopTotal(e), matches);
        } finally {
            tempData.cleanup(run);
        }
    }

    private Person person(boolean showed, boolean otherShowed, int deposit, String paymentId) {
        Integer refunded = jdbcTemplate.queryForObject(
            "select refunded_amount from payment_tb where payment_id = ?", Integer.class, paymentId);
        int refund = refunded == null ? 0 : refunded;
        return new Person(showed, deposit, refund, expectedRefund(showed, otherShowed, deposit), deposit - refund);
    }

    // 기획서 3.3절: 둘 다 옴 → 20% 중 15% 환불(보증금의 75%), 둘 다 노쇼 → 10% 환불(50%), 한쪽만 노쇼 → 온 사람 전액, 안 온 사람 0
    private static int expectedRefund(boolean showed, boolean otherShowed, int deposit) {
        if (showed && otherShowed) return Math.round(deposit * 0.75f);
        if (!showed && !otherShowed) return Math.round(deposit * 0.5f);
        return showed ? deposit : 0;
    }

    private int shopTotal(ExchangeRequest e) {
        Integer sum = jdbcTemplate.queryForObject(
            "select coalesce(sum(amount), 0) from settlement_history_tb where shop_id = ?", Integer.class, e.getShop().getId());
        return sum == null ? 0 : sum;
    }
}
