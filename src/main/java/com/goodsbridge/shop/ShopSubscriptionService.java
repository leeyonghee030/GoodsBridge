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

    // 일 단위 표준 요금 정책 (1일 = 1,000원)
    private static final int PRICE_PER_DAY = 1000;

    // 일 단위 구독 시작 및 기간 연장(누적)
    @Transactional
    public Long create(Long userId, Long shopId, ShopSubscriptionRequest request) {
        if (request.getDays() == null || request.getDays() <= 0) {
            throw new BadRequestException("구독 일수는 1일 이상이어야 합니다.");
        }

        if (request.getPaymentId() == null || request.getPaymentId().isBlank()) {
            throw new BadRequestException("결제 ID가 필요합니다.");
        }

        // [방어 1: 중복 결제 방지] 이미 사용된 paymentId 사전 검증
        if (subscriptionRepository.existsByPaymentId(request.getPaymentId())) {
            throw new BadRequestException("이미 처리된 결제 번호(paymentId)입니다. 중복 구독을 진행할 수 없습니다.");
        }

        // [방어 2: 위변조 방지] 서버 단 결제 금액 검증
        int expectedAmount = request.getDays() * PRICE_PER_DAY;
        if (request.getAmount() == null || !request.getAmount().equals(expectedAmount)) {
            throw new BadRequestException("결제 금액 정보가 올바르지 않습니다.");
        }

        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));

        userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자입니다."));

        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인의 가게만 구독을 시작하거나 갱신할 수 있습니다.");
        }

        ShopSubscription existingSubscription = subscriptionRepository.findByShopId(shopId).orElse(null);
        boolean isActive = existingSubscription != null && existingSubscription.getStatus() == ShopSubscriptionStatus.ACTIVE;

        // [외부 연동] 결제 검증 연동
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

            // 만료되었던 가게가 구독을 재개한 경우 CLOSED 상품들을 ON_SALE로 복구
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

    // 가게 단건 구독 조회
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
     심플 청크 배치] Page 0 반복 조회 방식
     * - 이미 처리된 항목은 ACTIVE 상태를 벗어나므로 0번 페이지만 조회해도 모든 데이터를 안전하게 순회합니다.
     */
    @Transactional(readOnly = true)
    public int processExpiredSubscriptions() {
        int pageSize = 100;
        int totalProcessedCount = 0;
        LocalDate today = LocalDate.now();

        log.info("[청크 배치 시작] 만료 구독 일괄 처리 (PageSize: {})", pageSize);

        while (true) {
            // 항상 0번 페이지를 조회 (처리된 데이터는 쿼리 조건에서 제외되므로 다음 데이터가 0번으로 당겨짐)
            Pageable pageable = PageRequest.of(0, pageSize);
            List<ShopSubscription> expiredSubscriptions =
                subscriptionRepository.findExpiredActiveSubscriptions(today, pageable);

            if (expiredSubscriptions.isEmpty()) {
                break; // 더 이상 조회되는 데이터가 없으면 루프 탈출
            }

            for (ShopSubscription subscription : expiredSubscriptions) {
                try {
                    // 개별 독립 트랜잭션(REQUIRES_NEW)으로 실행하여 실패 격리
                    boolean success = subscriptionProcessor.processSingleSubscription(subscription);
                    if (success) {
                        totalProcessedCount++;
                    }
                } catch (Exception e) {
                    log.error("[배치 개별 실패 격리] subscriptionId: {}, 사유: {}", subscription.getId(), e.getMessage());
                }
            }
        }

        log.info("[청크 배치 완료] 총 {}건의 만료 구독 처리 완료", totalProcessedCount);
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
