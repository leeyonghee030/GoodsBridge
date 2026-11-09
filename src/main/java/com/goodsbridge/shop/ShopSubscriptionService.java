package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemService;
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
    private final ItemService itemService; // 👈 ItemRepository 대신 ItemService 주입
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final ShopSubscriptionProcessor subscriptionProcessor;

    private static final int PRICE_PER_DAY = 1000;
    private static final int MAX_DAYS = 1095; // 한 번에 최대 3년(1095일) 제한

    @Transactional
    public Long create(Long userId, Long shopId, ShopSubscriptionRequest request) {
        if (request.getDays() == null || request.getDays() <= 0) {
            throw new BadRequestException("구독 일수는 1일 이상이어야 합니다.");
        }
        if (request.getDays() > MAX_DAYS) {
            throw new BadRequestException("한 번에 최대 3년(1095일)까지만 구독할 수 있습니다.");
        }

        if (request.getPaymentId() == null || request.getPaymentId().isBlank()) {
            throw new BadRequestException("결제 ID가 필요합니다.");
        }

        // 정수 오버플로 방어 계산
        long calculatedAmountLong = (long) request.getDays() * PRICE_PER_DAY;
        if (calculatedAmountLong > Integer.MAX_VALUE) {
            throw new BadRequestException("결제 금액이 허용 범위를 초과했습니다.");
        }
        int expectedAmount = (int) calculatedAmountLong;

        if (request.getAmount() == null || !request.getAmount().equals(expectedAmount)) {
            throw new BadRequestException("결제 금액 정보가 올바르지 않습니다.");
        }

        // 샵 조회 및 비관적 락 적용 (샵 삭제/폐점 트랜잭션과 경쟁 상태 방지)
        Shop shop = shopRepository.findByIdWithLock(shopId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));

        // 폐점·삭제·미승인 샵 구독 결제 차단 방어 로직
        if (shop.isDeleted()) {
            throw new BadRequestException("이미 삭제(폐점)된 가게는 구독을 진행할 수 없습니다.");
        }
        if (shop.getStatus() != ShopStatus.APPROVED) {
            throw new BadRequestException("승인 완료(APPROVED)된 가게만 구독을 진행할 수 있습니다. (현재 상태: " + shop.getStatus() + ")");
        }

        // [중복 결제 방어] DB 레벨 제약 및 exists 체크를 통한 이중 방어
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

        // 외부 결제 검증
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
                // 💡 [개선] ItemService에 상품 복구 및 상태 변경 책임을 위임합니다.
                itemService.restoreAndActivateItemsByShopId(shopId);

                log.info("[일 단위 구독 재개 성공] 가게 ID: {} | 상품들이 복구 및 ON_SALE로 전환되었습니다.", shopId);
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

        // 💡 [개선] ItemService에 상품 복구 위임
        itemService.restoreAndActivateItemsByShopId(shopId);

        log.info("[구독 및 상품 복구 완료] 가게 ID: {}", shopId);
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

    @Transactional(readOnly = true)
    public int processExpiredSubscriptions() {
        int pageSize = 100;
        Long lastId = 0L;
        int totalProcessedCount = 0;
        LocalDate today = LocalDate.now();

        log.info("[커서 페이징 배치 시작] 만료 구독 일괄 처리 (PageSize: {})", pageSize);

        while (true) {
            Pageable pageable = PageRequest.of(0, pageSize);
            List<ShopSubscription> expiredSubscriptions =
                subscriptionRepository.findExpiredActiveSubscriptionsAfterId(lastId, today, pageable);

            if (expiredSubscriptions.isEmpty()) {
                break;
            }

            for (ShopSubscription subscription : expiredSubscriptions) {
                lastId = subscription.getId();

                try {
                    boolean success = subscriptionProcessor.processSingleSubscriptionById(subscription.getId());
                    if (success) {
                        totalProcessedCount++;
                    }
                } catch (Exception e) {
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
