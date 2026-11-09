package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopSubscriptionService {

    private final ShopSubscriptionRepository subscriptionRepository;
    private final ShopRepository shopRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final ShopSubscriptionProcessor subscriptionProcessor;

    private static final int PRICE_PER_DAY = 1000;

    @Transactional
    public Long create(Long userId, Long shopId, ShopSubscriptionRequest request) {
        if (request.getDays() == null || request.getDays() <= 0) {
            throw new BadRequestException("구독 일수는 1일 이상이어야 합니다.");
        }

        if (request.getPaymentId() == null || request.getPaymentId().isBlank()) {
            throw new BadRequestException("결제 ID가 필요합니다.");
        }

        int expectedAmount = request.getDays() * PRICE_PER_DAY;
        if (request.getAmount() == null || !request.getAmount().equals(expectedAmount)) {
            throw new BadRequestException("결제 금액 정보가 올바르지 않습니다.");
        }

        // 💡 [수정 포인트] 일반 findById 대신 비관적 락이 걸리는 findByIdWithLock 사용!
        Shop shop = shopRepository.findByIdWithLock(shopId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));

        if (subscriptionRepository.existsByPaymentId(request.getPaymentId())) {
            throw new BadRequestException("이미 처리된 결제 번호(paymentId)입니다. 중복 구독을 진행할 수 없습니다.");
        }


        userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자입니다."));

        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인의 가게만 구독을 시작하거나 갱신할 수 있습니다.");
        }

        ShopSubscription existingSubscription = subscriptionRepository.findActiveByShopIdWithLock(shopId).orElse(null);
        boolean isActive = existingSubscription != null && existingSubscription.getStatus() == ShopSubscriptionStatus.ACTIVE;

        paymentService.verify(
            request.getPaymentId(),
            request.getAmount(),
            PaymentPurpose.SHOP_SUBSCRIPTION,
            userId
        );

        boolean setupFeePaid = true;
        boolean isExpiredOrCancelled = (existingSubscription != null &&
            (existingSubscription.getStatus() == ShopSubscriptionStatus.EXPIRED ||
                existingSubscription.getStatus() == ShopSubscriptionStatus.CANCELLED));

        LocalDate startDate;
        LocalDate endDate;
        ShopSubscription subscription;

        if (isActive) {
            LocalDate currentEndDate = existingSubscription.getEndDate() != null ? existingSubscription.getEndDate() : LocalDate.now();
            LocalDate baseDate = currentEndDate.isAfter(LocalDate.now()) ? currentEndDate : LocalDate.now();

            startDate = baseDate;
            endDate = baseDate.plusDays(request.getDays());

            existingSubscription.extendSubscription(startDate, endDate, setupFeePaid, request.getPaymentId());
            subscription = existingSubscription;
        } else {
            startDate = LocalDate.now();
            endDate = startDate.plusDays(request.getDays());

            subscription = subscriptionRepository.findByShopId(shopId)
                .map(sub -> {
                    sub.update(startDate, endDate, setupFeePaid, ShopSubscriptionStatus.ACTIVE, request.getPaymentId());
                    return sub;
                })
                .orElseGet(() -> ShopSubscription.builder()
                    .shopId(shopId)
                    .setupFeePaid(setupFeePaid)
                    .startDate(startDate)
                    .endDate(endDate)
                    .status(ShopSubscriptionStatus.ACTIVE)
                    .paymentId(request.getPaymentId())
                    .build());
        }

        try {
            Long subscriptionId = subscriptionRepository.save(subscription).getId();

            if (isExpiredOrCancelled || existingSubscription == null) {
                itemRepository.updateStatusByShopIdAndStatus(shopId, ItemStatus.CLOSED, ItemStatus.ON_SALE);
                log.info("[일 단위 구독 재개 성공] 가게 ID: {} | 판매 중단 상품들이 ON_SALE로 전환되었습니다.", shopId);
            }

            return subscriptionId;

        } catch (DataIntegrityViolationException e) {
            log.error("[구독 중복 생성 충돌] 가게 ID: {}", shopId, e);
            throw new BadRequestException("이미 처리 중이거나 존재하는 구독 정보(또는 결제 번호)입니다.");
        }
    }

    public ShopSubscriptionResponse findById(Long userId, Long shopId) {
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));

        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인의 가게 구독 정보만 조회 가능합니다.");
        }

        ShopSubscription subscription = subscriptionRepository.findByShopId(shopId)
            .orElseThrow(() -> new NotFoundException("구독 정보가 존재하지 않습니다."));

        return ShopSubscriptionResponse.from(subscription);
    }

    @Transactional
    public void restoreSubscriptionAndItems(Long shopId, String originalPaymentId) {
        ShopSubscription subscription = subscriptionRepository.findDeletedByShopId(shopId)
            .orElseThrow(() -> new NotFoundException("복구할 삭제된 구독 정보를 찾을 수 없습니다."));

        subscription.restore(originalPaymentId);

        List<Item> deletedItems = itemRepository.findAllDeletedByShopId(shopId);
        for (Item item : deletedItems) {
            item.restore(ItemStatus.ON_SALE);
        }

        log.info("[구독 및 상품 복구 완료] 가게 ID: {} | 복구된 상품 수: {}개", shopId, deletedItems.size());
    }

    public List<ShopSubscriptionResponse> findAllSubscriptions(Long adminUserId) {
        validateAdmin(adminUserId);
        return subscriptionRepository.findAll().stream()
            .map(ShopSubscriptionResponse::from)
            .toList();
    }

    public int processExpiredSubscriptionsByAdmin() {
        log.info("[관리자 수동 트리거] 만료 구독 배치 강제 실행");
        return processExpiredSubscriptions();
    }

    /**
     * [ID 커서 페이징 기반 만료 구독 일괄 처리 배치]
     * - OFFSET 방식의 성능 저하 및 데이터 밀림 현상(무한 루프)을 원천 차단합니다.
     * - 개별 건 처리 중 예외가 발생해도 트랜잭션이 분리되어 있어 전체 배치가 멈추지 않고 안전하게 격리됩니다.
     */
    @Transactional(readOnly = true)
    public int processExpiredSubscriptions() {
        int pageSize = 100;
        Long lastId = 0L; // 💡 첫 조회는 0번 ID 초과부터 시작
        int totalProcessedCount = 0;
        LocalDate today = LocalDate.now();

        log.info("[커서 페이징 배치 시작] 만료 구독 일괄 처리 (PageSize: {})", pageSize);

        while (true) {
            // 💡 커서 페이징을 위해 0번 페이지 크기만 100개로 지정하여 조회
            Pageable pageable = PageRequest.of(0, pageSize);
            List<ShopSubscription> expiredSubscriptions =
                subscriptionRepository.findExpiredActiveSubscriptionsAfterId(lastId, today, pageable);

            // 💡 더 이상 처리할 만료 구독이 없으면 확실하게 루프 탈출
            if (expiredSubscriptions.isEmpty()) {
                break;
            }

            for (ShopSubscription subscription : expiredSubscriptions) {
                // 💡 현재 순회 중인 엔티티의 ID로 커서를 무조건 갱신 (실패/스킵되어도 다음 ID로 전진하여 무한 루프 방지)
                lastId = subscription.getId();

                try {
                    // 💡 개별 프로세서 호출 (REQUIRES_NEW 트랜잭션으로 독립 실행)
                    boolean success = subscriptionProcessor.processSingleSubscriptionById(subscription.getId());
                    if (success) {
                        totalProcessedCount++;
                    }
                } catch (Exception e) {
                    // 💡 개별 실패 격리: 특정 건에 에러가 나도 로그만 남기고 다음 건으로 정상 진행
                    log.error("[배치 개별 실패 격리] subscriptionId: {}, 사유: {}", subscription.getId(), e.getMessage());
                }
            }
        }

        log.info("[커서 페이징 배치 완료] 총 {}건의 만료 구독 처리 완료", totalProcessedCount);
        return totalProcessedCount;
    }

    public boolean isSubscriptionActive(Long shopId) {
        return subscriptionRepository.findByShopId(shopId)
            .map(sub -> sub.getStatus() == ShopSubscriptionStatus.ACTIVE
                && sub.getEndDate() != null
                && !sub.getEndDate().isBefore(LocalDate.now()))
            .orElse(false);
    }

    private void validateAdmin(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자입니다."));
        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("관리자 권한이 없습니다.");
        }
    }
}
