package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 이슈 #12 유효성 검사 단위 테스트 (DB/스프링 컨텍스트 없이 검증)
 * - startPrice > 0
 * - depositAmount는 startPrice의 10%~20%
 * - deadline은 미래 시각
 */
class AuctionRequestTest {

    private AuctionRequest request(Long itemId, Integer startPrice, Integer depositAmount, LocalDateTime deadline) {
        AuctionRequest request = new AuctionRequest();
        ReflectionTestUtils.setField(request, "itemId", itemId);
        ReflectionTestUtils.setField(request, "startPrice", startPrice);
        ReflectionTestUtils.setField(request, "depositAmount", depositAmount);
        ReflectionTestUtils.setField(request, "deadline", deadline);
        return request;
    }

    private LocalDateTime future() {
        return LocalDateTime.now().plusHours(1);
    }

    @Test
    void validate_acceptsValidRequest() {
        assertDoesNotThrow(() -> request(1L, 50_000, 7_500, future()).validate());
    }

    @Test
    void validate_acceptsDepositExactlyAtTenAndTwentyPercent() {
        assertDoesNotThrow(() -> request(1L, 50_000, 5_000, future()).validate());   // 10%
        assertDoesNotThrow(() -> request(1L, 50_000, 10_000, future()).validate());  // 20%
    }

    @Test
    void validate_rejectsDepositJustBelowTenPercent() {
        BadRequestException e = assertThrows(BadRequestException.class,
            () -> request(1L, 50_000, 4_999, future()).validate());
        assertEquals("보증금은 시작가의 10% 이상 20% 이하여야 합니다.", e.getMessage());
    }

    @Test
    void validate_rejectsDepositJustAboveTwentyPercent() {
        BadRequestException e = assertThrows(BadRequestException.class,
            () -> request(1L, 50_000, 10_001, future()).validate());
        assertEquals("보증금은 시작가의 10% 이상 20% 이하여야 합니다.", e.getMessage());
    }

    @Test
    void validate_doesNotOverflowForLargeStartPrice() {
        // int로 계산하면 overflow가 나는 값: Integer.MAX_VALUE의 10%보다 아주 조금 큰 보증금
        int startPrice = Integer.MAX_VALUE;
        int deposit = Integer.MAX_VALUE / 10 + 1;

        assertDoesNotThrow(() -> request(1L, startPrice, deposit, future()).validate());
    }

    @Test
    void validate_rejectsMissingItemId() {
        BadRequestException e = assertThrows(BadRequestException.class,
            () -> request(null, 50_000, 5_000, future()).validate());
        assertEquals("상품 ID는 필수입니다.", e.getMessage());
    }

    @Test
    void validate_rejectsNullZeroOrNegativeStartPrice() {
        assertThrows(BadRequestException.class, () -> request(1L, null, 5_000, future()).validate());
        assertThrows(BadRequestException.class, () -> request(1L, 0, 5_000, future()).validate());
        BadRequestException e = assertThrows(BadRequestException.class,
            () -> request(1L, -1, 5_000, future()).validate());
        assertEquals("시작가는 0보다 커야합니다.", e.getMessage());
    }

    @Test
    void validate_rejectsNullZeroOrNegativeDeposit() {
        assertThrows(BadRequestException.class, () -> request(1L, 50_000, null, future()).validate());
        assertThrows(BadRequestException.class, () -> request(1L, 50_000, 0, future()).validate());
        BadRequestException e = assertThrows(BadRequestException.class,
            () -> request(1L, 50_000, -5_000, future()).validate());
        assertEquals("보증금은 0보다 커야합니다.", e.getMessage());
    }

    @Test
    void validate_rejectsNullOrPastDeadline() {
        assertThrows(BadRequestException.class, () -> request(1L, 50_000, 5_000, null).validate());
        BadRequestException e = assertThrows(BadRequestException.class,
            () -> request(1L, 50_000, 5_000, LocalDateTime.now().minusSeconds(1)).validate());
        assertEquals("마감 시간은 미래 시간이어야 합니다.", e.getMessage());
    }
}
