package com.goodsbridge.auction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 1) 마감 시간이 지난 ACTIVE 경매를 종료
 * 2) 종료된 경매의 패찰자 보증금 환불
 * 3) 잔금 결제 기한이 지난 낙찰 경매를 미납 처리
 * 4) 취소된 경매의 입찰자 보증금 전액 환불
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduler {
    private static final int PAGE_SIZE = 100;

    private final AuctionRepository auctionRepository;
    private final AuctionService auctionService;

    // 정해진 시간마다 경매 마감 -> 환불 처리-> 잔금 처리 -> 취소 시 처리
    @Scheduled(fixedDelay = 60000)
    public void autoCloseAuctions() {
        runStep("경매 자동 마감", this::closeExpiredAuctions);
        runStep("패찰자 보증금 환불", this::refundLosers);
        runStep("잔금 미납 처리", this::forfeitUnpaidWinners);
        runStep("취소 경매 보증금 환불", this::refundCancelledAuctions);
    }

    // 한 단계가 통째로 실패해도 다음 단계는 계속 돈다
    private void runStep(String name, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            log.error("{} 단계 실패", name, e);
        }
    }

    // 경매 마감
    private void closeExpiredAuctions() {
        LocalDateTime now = LocalDateTime.now();
        // 실패한 경매는 ACTIVE로 남아 다음 조회에도 다시 나오므로, 건너뛰어야 무한 반복을 막을 수 있다.
        Set<Long> failedIds = new HashSet<>();

        while (true) {
            // 종료 처리된 경매는 ACTIVE 목록에서 빠지므로 항상 첫 페이지를 다시 조회한다.
            // 실패 건수만큼 크기를 늘려, 건너뛴 뒤에도 새 대상이 PAGE_SIZE개 확보되게 한다.
            Page<Auction> page = auctionRepository.findByStatusAndDeadlineBefore(
                Status.ACTIVE, now,
                PageRequest.of(0, PAGE_SIZE + failedIds.size(), Sort.by(Sort.Direction.ASC, "id")));

            List<Long> targets = page.getContent().stream()
                .map(Auction::getId)
                .filter(id -> !failedIds.contains(id))
                .toList();
            if (targets.isEmpty()) {
                return;
            }

            for (Long id : targets) {
                try {
                    auctionService.closeExpired(id);
                } catch (RuntimeException e) {
                    failedIds.add(id);
                    log.error("경매 자동 마감 실패 - auctionId={}", id, e);
                }
            }
        }
    }

    // 패찰자 환불
    private void refundLosers() {
        List<Long> ids = auctionRepository.findIdsPendingLoserRefund(
            List.of(Status.ENDED_SUCCESS, Status.ENDED_FAIL));
        for (Long id : ids) {
            try {
                auctionService.refundLosers(id);
                auctionService.markLosersRefunded(id);
            } catch (RuntimeException e) {
                log.error("패찰자 보증금 환불 실패 - auctionId={} (다음 주기에 재시도)", id, e);
            }
        }
    }

    // 경매 도중 취소 시 환불
    private void refundCancelledAuctions() {
        List<Long> ids = auctionRepository.findIdsPendingCancelRefund(Status.CANCELLED);
        for (Long id : ids) {
            try {
                auctionService.refundCancelled(id);
                auctionService.markLosersRefunded(id);
            } catch (RuntimeException e) {
                log.error("취소 경매 보증금 환불 실패 - auctionId={} (다음 주기에 재시도)", id, e);
            }
        }
    }

    // 잔금 납부 여부
    private void forfeitUnpaidWinners() {
        List<Long> ids = auctionRepository.findIdsUnpaidOverdue(Status.ENDED_SUCCESS, LocalDateTime.now());
        for (Long id : ids) {
            try {
                auctionService.forfeitUnpaid(id);
            } catch (RuntimeException e) {
                log.error("잔금 미납 처리 실패 - auctionId={}", id, e);
            }
        }
    }
}
