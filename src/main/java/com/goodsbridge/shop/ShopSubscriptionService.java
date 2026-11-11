package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge._core.util.SecurityUtil;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;
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

            // 💡 방어 로직: 연장하려는 총 일수가 오늘 기준 최대 허용 기간(1095일)을 넘지 않는지 검증
            long totalDaysFromNow = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), baseDate.plusDays(request.getDays()));
            if (totalDaysFromNow > MAX_DAYS) {
                throw new BadRequestException("구독 기간은 오늘부터 최대 3년(1095일)까지만 누적 연장할 수 있습니다.");
            }

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
                // 💡 [이벤트 발행] 직접 호출 대신 이벤트를 발행하여 결합도를 완전히 제거합니다.
                eventPublisher.publishEvent(new ShopSubscriptionActivatedEvent(shopId));

                log.info("[일 단위 구독 재개 성공] 가게 ID: {} | 상품들이 복구 및 ON_SALE로 전환되었습니다.", shopId);
            }

            return subscriptionId;

        } catch (DataIntegrityViolationException e) {
            log.error("[구독 중복 생성 충돌] 가게 ID: {}", shopId, e);
            throw new BadRequestException("이미 처리 중이거나 존재하는 구독 정보(또는 결제 번호)입니다.");
        }
    }

    /**
     * 💡 [추가] 구독 취소 및 상품 차단 이벤트 발행 로직
     */
    @Transactional
    public void cancelSubscription(Long userId, Long shopId) {
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));

        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인의 가게 구독만 취소할 수 있습니다.");
        }

        ShopSubscription subscription = subscriptionRepository.findActiveByShopIdWithLock(shopId)
            .orElseThrow(() -> new NotFoundException("활성화된 구독 정보를 찾을 수 없습니다."));

        subscription.cancel();

        // 💡 구독 취소 이벤트 발행 (이벤트를 통해 Item 측에서 상품들을 CLOSED 처리하도록 위임)
        eventPublisher.publishEvent(new ShopSubscriptionCanceledEvent(shopId));

        log.info("[구독 취소 완료] 가게 ID: {} | 구독 취소 이벤트가 발행되었습니다.", shopId);
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

    public List<ShopSubscriptionResponse> findAllSubscriptions(Long adminUserId) {
        validateAdmin(adminUserId);
        return subscriptionRepository.findAll().stream()
            .map(ShopSubscriptionResponse::from)
            .toList();
    }

    @Transactional
    public int processExpiredSubscriptionsByAdmin() {
        // 1. 현재 요청을 보낸 사용자의 ID를 추출하여 관리자 권한 검증
        Long adminUserId = SecurityUtil.getCurrentUserId();
        User adminUser = userRepository.findById(adminUserId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자입니다."));

        if (adminUser.getRole() != Role.ADMIN) {
            throw new ForbiddenException("관리자 권한만 구독 만료 배치를 수동 실행할 수 있습니다.");
        }

        log.info("[관리자 수동 트리거] 관리자(ID: {})에 의해 만료 구독 배치 강제 실행", adminUserId);

        // 2. 기존 커서 페이징 배치 로직 호출
        return processExpiredSubscriptions();
    }

    // [스케줄러 & 관리자 공용] 커서 페이징을 활용한 만료 구독 일괄 처리 핵심 로직
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
                    // 개별 트랜잭션(REQUIRES_NEW)으로 분리된 프로세서 호출
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
