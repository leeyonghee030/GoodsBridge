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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopSubscriptionService {

    private final ShopSubscriptionRepository subscriptionRepository;
    private final ShopRepository shopRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;

    // 월 단위 구독 시작 && 갱신
    public Long create(Long userId, Long shopId, ShopSubscriptionRequest request) {

        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자입니다."));

        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인의 가게만 구독은 시작하거나 갱신할 수 있습니다.");
        }

        ShopSubscription existingSubscription = subscriptionRepository.findByShopId(shopId).orElse(null);

        boolean isActive = existingSubscription != null && existingSubscription.getStatus() == ShopSubscriptionStatus.ACTIVE;

        // 1. 일수 유효성 검사
        if (request.getDays() <= 0) {
            throw new BadRequestException("구독 일수는 1일 이상이어야 합니다.");
        }

        if (request.getPaymentId() == null || request.getPaymentId().isBlank()) {
            throw new BadRequestException("결제 ID가 필요합니다.");
        }

        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new BadRequestException("결제 금액 정보가 올바르지 않습니다.");
        }

        // 포트원 결제 검증 연동
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
            //ACTIVE 상태인 구독이 기간이 남아있을 때: 기존 종료일(endDate)에 새로운 구독 기간을 합산(Add)
            LocalDate baseDate = existingSubscription.getEndDate().isAfter(LocalDate.now())
                ? existingSubscription.getEndDate()
                : LocalDate.now();

            // 연장 시점의 시작일은 기존 시작일 유지 또는 오늘(또는 baseDate)로 설정 가능
            // 보통 갱신 시점의 새 시작일은 기존 만료일(또는 오늘)이 됩니다.
            startDate = existingSubscription.getEndDate().isAfter(LocalDate.now())
                ? existingSubscription.getEndDate()
                : LocalDate.now();

            endDate = baseDate.plusDays(request.getDays()); // 월 단위 연장

            existingSubscription.extendSubscription(startDate, endDate, setupFeePaid);
            subscription = existingSubscription;
        } else {
            // 신규 가입 또는 만료/취소되었던 가게의 재시작
            startDate = LocalDate.now();
            endDate = startDate.plusDays(request.getDays());// 오늘부터 N일 뒤

            subscription = subscriptionRepository.findByShopId(shopId)
                .map(sub -> {
                    sub.update(startDate, endDate, setupFeePaid, ShopSubscriptionStatus.ACTIVE);
                    return sub;
                })
                .orElseGet(() -> ShopSubscription.builder()
                    .shopId(shopId)
                    .setupFeePaid(setupFeePaid)
                    .startDate(startDate)
                    .endDate(endDate)
                    .status(ShopSubscriptionStatus.ACTIVE)
                    .build());
        }

        Long subscriptionId = subscriptionRepository.save(subscription).getId();

        // 만료되었던 가게가 구독을 재개한 경우 CLOSED 상품들을 ON_SALE로 복구
        if (isExpiredOrCancelled || existingSubscription == null) {
            itemRepository.updateStatusByShopIdAndStatus(shopId, ItemStatus.CLOSED, ItemStatus.ON_SALE);
        }

        return subscriptionId;

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
