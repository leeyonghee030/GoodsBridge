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
 * 1분마다 아래 세 가지를 순서대로 처리한다. 각 단계는 경매 1건 단위로 실패를 격리한다.
 * 1) 마감 시간이 지난 ACTIVE 경매를 종료 (낙찰이면 잔금 결제 기한 설정)
 * 2) 종료된 경매의 패찰자 보증금 환불 (실패하면 다음 주기에 재시도)
 * 3) 잔금 결제 기한이 지난 낙찰 경매를 미납 처리 (낙찰 무효, 보증금은 가게 몫)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduler {
    private static final int PAGE_SIZE = 100;

    private final AuctionRepository auctionRepository;
    private final AuctionService auctionService;

    @Scheduled(fixedDelay = 60000)
    public void autoCloseAuctions() {
        runStep("경매 자동 마감", this::closeExpiredAuctions);
        runStep("패찰자 보증금 환불", this::refundLosers);
        runStep("잔금 미납 처리", this::forfeitUnpaidWinners);
    }

    // 한 단계가 통째로 실패해도 다음 단계는 계속 돈다
    private void runStep(String name, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            log.error("{} 단계 실패", name, e);
        }
    }

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

    private void refundLosers() {
        // 낙찰자 미납으로 ENDED_FAIL이 된 경매도 포함 — 환불이 밀려 있는 채로 상태가 바뀌어도 끝까지 환불한다
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
