package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
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

    // 일 단위 표준 요금 정책 (예: 1일 = 1,000원 -> 30일 = 30,000원)
    private static final int PRICE_PER_DAY = 1000;

    // 일 단위 구독 시작 && 기간 연장(누적)
    @Transactional
    public Long create(Long userId, Long shopId, ShopSubscriptionRequest request) {

        // 1. 일수 및 결제 ID 유효성 검사
        if (request.getDays() == null || request.getDays() <= 0) {
            throw new BadRequestException("구독 일수는 1일 이상이어야 합니다.");
        }

        if (request.getPaymentId() == null || request.getPaymentId().isBlank()) {
            throw new BadRequestException("결제 ID가 필요합니다.");
        }

        // [방어 1: 중복 결제 방지] 이미 사용된 paymentId인지 사전 검증 (Fast-Fail)
        if (subscriptionRepository.existsByPaymentId(request.getPaymentId())) {
            throw new BadRequestException("이미 처리된 결제 번호(paymentId)입니다. 중복 구독을 진행할 수 없습니다.");
        }

        // [방어 2: 위변조 방지] 서버 단 결제 금액 검증
        int expectedAmount = request.getDays() * PRICE_PER_DAY;
        if (request.getAmount() == null || !request.getAmount().equals(expectedAmount)) {
            throw new BadRequestException("결제 금액 정보가 올바르지 않습니다. (요청 금액: " + request.getAmount() + ", 정가: " + expectedAmount + ")");
        }

        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자입니다."));

        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인의 가게만 구독을 시작하거나 갱신할 수 있습니다.");
        }

        ShopSubscription existingSubscription = subscriptionRepository.findByShopId(shopId).orElse(null);

        boolean isActive = existingSubscription != null && existingSubscription.getStatus() == ShopSubscriptionStatus.ACTIVE;

        // [외부 연동] 포트원 결제 검증 연동
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
            // [방어 3: Null 방어] 기존 만료일 안전하게 추출
            LocalDate currentEndDate = existingSubscription.getEndDate() != null ? existingSubscription.getEndDate() : LocalDate.now();

            // ACTIVE 상태인 구독이 기간이 남아있을 때: 기존 종료일에 새로운 일수 합산
            LocalDate baseDate = currentEndDate.isAfter(LocalDate.now())
                ? currentEndDate
                : LocalDate.now();

            startDate = currentEndDate.isAfter(LocalDate.now())
                ? currentEndDate
                : LocalDate.now();

            endDate = baseDate.plusDays(request.getDays());

            existingSubscription.extendSubscription(startDate, endDate, setupFeePaid, request.getPaymentId());
            subscription = existingSubscription;
        } else {
            // 신규 가입 또는 만료/취소되었던 가게의 재시작
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
                    .paymentId(request.getPaymentId()) // [결제 번호 저장]
                    .build());
        }

        try {
            Long subscriptionId = subscriptionRepository.save(subscription).getId();

            // 만료되었던 가게가 구독을 재개한 경우 CLOSED 상품들을 ON_SALE로 복구 + 운영 로그
            if (isExpiredOrCancelled || existingSubscription == null) {
                itemRepository.updateStatusByShopIdAndStatus(shopId, ItemStatus.CLOSED, ItemStatus.ON_SALE);
                log.info("[일 단위 구독 재개 성공] 가게 ID: {} | 연장 일수: {}일 | 판매 중단 상품들이 ON_SALE로 전환되었습니다.", shopId, request.getDays());
            }

            return subscriptionId;

        } catch (DataIntegrityViolationException e) {
            // [방어 4: 동시성 충돌 및 유니크 제약 방어]
            log.error("[구독 중복 생성 충돌] 가게 ID: {} 또는 결제 번호 충돌이 감지되었습니다.", shopId, e);
            throw new BadRequestException("이미 처리 중이거나 존재하는 구독 정보(또는 결제 번호)입니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    // 가게 단건 구독 조회 (가게 사장 본인)
    public ShopSubscriptionResponse findById(Long userId, Long shopId) {
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));

        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인의 가게 구독 정보만 조회 가능합니다.");
        }

        ShopSubscription subscription = subscriptionRepository.findByShopId(shopId)
            .orElseThrow(() -> new NotFoundException("구독 정보가 존재하지 않습니다"));

        return ShopSubscriptionResponse.from(subscription);
    }

    // (관리자) 전체 가게 구독 현황 목록 조회
    public List<ShopSubscriptionResponse> findAllSubscriptions(Long adminUserId) {
        validateAdmin(adminUserId);
        return subscriptionRepository.findAll().stream()
            .map(ShopSubscriptionResponse::from)
            .toList();
    }

    // 가게 구독 활성 여부 확인 (상품 등록/수정 제한용)
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
