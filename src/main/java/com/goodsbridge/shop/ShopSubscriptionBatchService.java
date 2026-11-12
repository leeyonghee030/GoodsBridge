
package com.goodsbridge.shop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopSubscriptionBatchService {

    private static final int PAGE_SIZE = 100;

    private final ShopSubscriptionRepository subscriptionRepository;
    private final ShopSubscriptionProcessor subscriptionProcessor;

    /**
     * 만료된 ACTIVE 구독을 ID 커서 방식으로 일괄 처리한다.
     *
     * 배치 전체에는 트랜잭션을 적용하지 않는다.
     * 각 구독 처리는 Processor의 REQUIRES_NEW 트랜잭션으로 실행한다.
     */
    public int processExpiredSubscriptions() {

        Long lastId = 0L;

        int totalProcessedCount = 0;
        int totalFailureCount = 0;

        // 배치 실행 중 기준 날짜가 바뀌지 않도록 한 번만 조회
        LocalDate today = LocalDate.now();

        while (true) {

            // 만료된 구독의 ID만 100개씩 조회
            List<Long> subscriptionIds =
                subscriptionRepository.findExpiredActiveSubscriptionIdsAfterId(
                    lastId,
                    today,
                    ShopSubscriptionStatus.ACTIVE,
                    PageRequest.of(0, PAGE_SIZE)
                );

            if (subscriptionIds.isEmpty()) {
                break;
            }

            for (Long subscriptionId : subscriptionIds) {

                // 처리 성공 여부와 관계없이 커서는 앞으로 이동
                lastId = subscriptionId;

                try {
                    boolean success =
                        subscriptionProcessor.processSingleSubscriptionById(subscriptionId);

                    if (success) {
                        totalProcessedCount++;
                    }

                } catch (Exception e) {

                    // 한 건의 실패가 나머지 구독 처리를 중단시키지 않도록 격리
                    totalFailureCount++;

                    log.error(
                        "[만료 구독 개별 처리 실패] subscriptionId={}",
                        subscriptionId,
                        e
                    );
                }
            }

            // 마지막 페이지라면 반복 종료
            if (subscriptionIds.size() < PAGE_SIZE) {
                break;
            }
        }

        log.info(
            "[만료 구독 배치 완료] 성공={}건, 실패={}건",
            totalProcessedCount,
            totalFailureCount
        );

        return totalProcessedCount;
    }
}
