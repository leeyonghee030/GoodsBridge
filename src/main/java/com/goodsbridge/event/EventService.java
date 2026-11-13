package com.goodsbridge.event;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.event.dto.EventParticipationRequest;
import com.goodsbridge.event.dto.EventParticipationResponse;
import com.goodsbridge.event.dto.EventSettlementRecordResponse;
import com.goodsbridge.event.dto.EventSettlementResponse;
import com.goodsbridge.event.dto.ShopEventUpdateRequest;
import com.goodsbridge.event.dto.ShopEventRequest;
import com.goodsbridge.event.dto.ShopEventResponse;
import com.goodsbridge.item.Category;
import com.goodsbridge.item.CategoryJpaRepository;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.item.SaleType;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.settlement.SettlementHistoryService;
import com.goodsbridge.settlement.SettlementHistoryType;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.shop.ShopStatus;
import com.goodsbridge.shop.ShopSubscriptionService;
import com.goodsbridge.user.Status;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EventService {

    private final ShopRepository shopRepository;
    private final ItemRepository itemRepository;
    private  final  ShopEventRepository shopEventRepository;
    private final EntityManager entityManager;
    private final EventParticipationRepository eventParticipationRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final SettlementHistoryService settlementHistoryService;
    private final TransactionTemplate transactionTemplate;
    private final ShopSubscriptionService shopSubscriptionService;
    private final CategoryJpaRepository categoryRepository;

    /// ---------EventParticipation service

    // 참가 신청. 유저는 이미 PortOne 결제를 마치고 오므로, 서버가 거절하면(정원초과/중복/마감 등) 결제를 자동 취소한다.
    // 참가 처리 트랜잭션(이벤트 행 잠금)을 먼저 끝낸 뒤 취소해야, PortOne 호출 동안 다른 신청자가 잠금을 기다리지 않는다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EventParticipationResponse saveEventParticipation(Long id, Long userid, EventParticipationRequest request) {
        try {
            return transactionTemplate.execute(status -> doSaveEventParticipation(id, userid, request));
        } catch (RuntimeException e) {
            paymentService.cancelUnrecorded(request.getPaymentId(), "이벤트 참가 실패로 인한 자동 취소");
            throw e;
        }
    }

    private EventParticipationResponse doSaveEventParticipation(Long id, Long userid, EventParticipationRequest request) {
        //조회
         ShopEvent shopEvent = shopEventRepository.findByIdForUpdate(id).orElseThrow(()->{
                throw new NotFoundException("존재하지 않는 이벤트입니다");}
         );

        User user = userRepository.findById(userid).orElseThrow(()->{
            throw new NotFoundException("존재하지 않는 사용자 입니다");}
        );
        // 이벤트 상태확인
        if (shopEvent.getStatus() != ShopEventStatus.OPEN){
            throw new ForbiddenException("진행중인 이벤트에 참여가능합니다");
        }
        // 마감시간 확인 (스케줄러가 1분마다 돌아서 상태만으로는 마감 직후를 못 막는다)
        if (!shopEvent.isBeforeDeadline(LocalDateTime.now())) {
            throw new BadRequestException("마감된 이벤트입니다");
        }
        // 가게 구독이 끊기면 신규 참가만 막는다 (이미 참가한 사람은 그대로 유지)
        if (!shopSubscriptionService.isSubscriptionActive(shopEvent.getShop().getId())) {
            throw new BadRequestException("가게 구독이 종료되어 참가할 수 없는 이벤트입니다");
        }
        //유저 상태확인
        if (user.getStatus() != Status.ACTIVE){
            throw new ForbiddenException("활성화된 사용자만 참여 가능합니다");
        }
        // 가게 주인은 자기 가게 이벤트에 참가할 수 없다 (상품 구매와 같은 규칙)
        if (shopEvent.isShop(userid)) {
            throw new ForbiddenException("내 가게 이벤트에는 참가할 수 없습니다");
        }
        // 가게 계정은 다른 가게 이벤트에도 참가할 수 없다
        if (shopRepository.existsByOwnerUserIdAndDeletedFalse(userid)) {
            throw new ForbiddenException("가게 계정은 이벤트에 참가할 수 없습니다");
        }
        // 이미 참가한 이벤트면 추가 구매 (같은 번호표에 구매 건을 하나 더 쌓는다)
        List<EventParticipation> mine = eventParticipationRepository.findJoinedByUserIdAndShopEventId(userid, id);
        int alreadyQuantity = mine.stream().mapToInt(EventParticipation::getQuantity).sum();

        //이벤트 남은 수량 확인
        int quantity = request.getQuantity();
        if (shopEvent.getCurrentCount() >= shopEvent.getCapacity()){
            shopEvent.updateStatus(ShopEventStatus.CLOSED);
            throw new BadRequestException("정원 초과로 이벤트 참가 불가능합니다");
        }
        // 1인당 최대 수량은 이미 산 수량과 합쳐서 본다
        if (alreadyQuantity + quantity > shopEvent.getMaxPerUser()) {
            throw new BadRequestException("이 이벤트는 1인당 최대 " + shopEvent.getMaxPerUser() + "개까지 신청할 수 있습니다"
                + (alreadyQuantity > 0 ? " (이미 " + alreadyQuantity + "개 구매, 추가로 "
                + Math.max(0, shopEvent.getMaxPerUser() - alreadyQuantity) + "개까지 가능)" : ""));
        }
        if (shopEvent.getCurrentCount() + quantity > shopEvent.getCapacity()) {
            throw new BadRequestException("남은 수량(" + (shopEvent.getCapacity() - shopEvent.getCurrentCount()) + "개)보다 많이 신청할 수 없습니다");
        }
        //결제 확인 (참가비 × 수량)
        String paymentId = paymentService.verify(request.getPaymentId(), shopEvent.getFeeAmount() * quantity, PaymentPurpose.EVENT_FEE, userid);
        // 번호표는 1인 1장: 처음엔 참가중인 최대 번호 + 1, 추가 구매는 기존 번호표 그대로 (수량은 구매 건마다 따로 기록)
        int ticketNumber = mine.isEmpty()
            ? eventParticipationRepository.findMaxTicketNumber(id) + 1
            : mine.get(0).getTicketNumber();
        shopEvent.increaseCurrentCount(quantity);
        EventParticipation eventParticipation = EventParticipation.builder()
            .shopEvent(shopEvent)
            .user(user)
            .ticketNumber(ticketNumber)
            .quantity(quantity)
            .paymentId(paymentId)
            .build();

        if (shopEvent.getCurrentCount() >= shopEvent.getCapacity()){
            shopEvent.updateStatus(ShopEventStatus.CLOSED);}

        eventParticipationRepository.save(eventParticipation);
        eventParticipationRepository.flush();
        entityManager.refresh(eventParticipation);
        return new EventParticipationResponse(eventParticipation);
    }

    // 참가 취소: 구매 건(번호표에 쌓인 구매 1건) 단위로 취소/환불한다
    @Transactional
    public void cancelEventParticipation(Long id, Long userid, Long participationId) {
        //조회 (참가 신청과 동시에 인원수를 바꾸므로 같은 잠금을 건다)
        ShopEvent shopEvent = shopEventRepository.findByIdForUpdate(id).orElseThrow(()->{
            throw new NotFoundException("존재하지 않는 이벤트입니다");}
        );

        User user = userRepository.findById(userid).orElseThrow(()->{
            throw new NotFoundException("존재하지 않는 사용자 입니다");}
        );
        //유저 상태확인
        if (user.getStatus() != Status.ACTIVE){
            throw new ForbiddenException("활성화된 사용자만 참여 가능합니다");
        }
        // 취소된 이벤트는 가게 취소 때 이미 전원 환불됐고, 마감 후에는 환불하지 않는다
        if (shopEvent.getStatus() == ShopEventStatus.CANCELLED) {
            throw new BadRequestException("취소된 이벤트입니다");
        }
        if (!shopEvent.isBeforeDeadline(LocalDateTime.now())) {
            throw new BadRequestException("마감된 이벤트는 참가 취소할 수 없습니다");
        }

        // 내 구매 건이 맞는지 (다른 사람/다른 이벤트의 건은 없는 것으로 취급)
        EventParticipation eventParticipation = eventParticipationRepository.findById(participationId)
            .filter(p -> p.isJoined() && p.isOwner(userid) && p.getShopEvent().getId().equals(id))
            .orElseThrow(() -> new NotFoundException("참가 내역을 찾을 수 없습니다"));
        paymentService.refund(eventParticipation.getPaymentId(), shopEvent.getFeeAmount() * eventParticipation.getQuantity(), "이벤트 참가 취소");
        eventParticipation.cancel();
        // 수량 반납 (정원이 차서 마감됐던 이벤트는 다시 모집중)
        shopEvent.decreaseCurrentCount(eventParticipation.getQuantity());
    }


    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void autoCloseShopEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<ShopEvent> shopEvents = shopEventRepository.findSettleTargets(now);
        for (ShopEvent shopEvent : shopEvents) {
            shopEvent.updateStatus(ShopEventStatus.CLOSED);
            // 마감 시점에 남아있는 참가자 기준으로 참가비 전액을 가게 정산에 기록 (마감 후엔 참가 취소 불가)
            for (EventParticipation participation : eventParticipationRepository.findJoinedByShopEventId(shopEvent.getId())) {
                Payment payment = paymentRepository.findByPaymentId(participation.getPaymentId()).orElse(null);
                if (payment != null) {
                    settlementHistoryService.calculate(shopEvent.getShop(), payment, shopEvent.getFeeAmount() * participation.getQuantity(), SettlementHistoryType.EVENT_FEE);
                }
            }
            shopEvent.markSettled();
        }
    }



    /// ---------EventParticipation service


    /// ---------shopEvent service
    @Transactional
    public ShopEventResponse save(Long id, Long userId, ShopEventRequest request) {
        //shop 조회
        Shop shop = shopRepository.findById(id).orElseThrow(()->{
            throw new NotFoundException("존재하지 않는 가게입니다");
        });

        //로그인 유저가 가게 id 맞는지
        if (!shop.getOwnerUserId().equals(userId)){
            throw new ForbiddenException("본인 가게의 이벤트만 생성할수있습니다");
        }
        if (shop.getStatus() != ShopStatus.APPROVED){
            throw new BadRequestException("입점 승인된 가게만 이벤트를 할수있습니다.");
        }
        // 상품 등록과 같이 구독 중인 가게만 이벤트를 열 수 있다
        if (!shopSubscriptionService.isSubscriptionActive(id)) {
            throw new ForbiddenException("구독 중인 가게만 이벤트를 열 수 있습니다.");
        }
        // 이벤트 상품은 이벤트를 열면서 새로 등록한다 (판매유형 EVENT — SHOP 목록/일반 구매에서는 빠진다)
        Category category = categoryRepository.findById(request.getCategoryId()).orElseThrow(()->{
            throw new NotFoundException("존재하지 않는 카테고리입니다");
        });
        Item item = itemRepository.save(Item.builder()
            .sellerUserId(userId)
            .shopId(id)
            .category(category)
            .title(request.getItemTitle().trim())
            .description(request.getItemDescription())
            .price(request.getItemPrice())
            .saleType(SaleType.EVENT)
            .status(ItemStatus.ON_SALE)
            .imageUrl(request.getItemImageUrl())
            .build());

        ShopEvent shopEvent = ShopEvent.builder()
            .shop(shop)
            .item(item)
            .capacity(request.getCapacity())
            .feeAmount(request.getFeeAmount())
            .maxPerUser(request.getMaxPerUser())
            .deadline(request.getDeadline())
            .visitUntil(request.getVisitUntil())
            .build();
        shopEventRepository.save(shopEvent);
        shopEventRepository.flush();
        entityManager.refresh(shopEvent);
        return ShopEventResponse.from(shopEvent);
    }

    // 이벤트 수정. 설명/방문 날짜는 이벤트가 끝나기 전이면 가능하고, 수량/가격/마감 시각 같은 조건은
    // 참가자가 아직 없고 모집 마감 전일 때만 바꿀 수 있다 (참가한 사람이 낸 참가비 기준이 바뀌지 않게).
    @Transactional
    public ShopEventResponse updateEvent(Long id, Long userId, ShopEventUpdateRequest request) {
        ShopEvent shopEvent = shopEventRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 이벤트입니다"));
        if (!shopEvent.isShop(userId)) {
            throw new ForbiddenException("본인 가게의 이벤트만 수정할 수 있습니다");
        }
        if (shopEvent.getStatus() == ShopEventStatus.CANCELLED) {
            throw new BadRequestException("취소된 이벤트는 수정할 수 없습니다");
        }
        // 손님이 방문하는 날(오늘)부터는 이미 현장 진행 중이라 수정할 수 없다
        if (!shopEvent.getVisitUntil().isAfter(java.time.LocalDate.now())) {
            throw new BadRequestException("방문 날짜가 오늘이거나 지난 이벤트는 수정할 수 없습니다");
        }
        if (request.changesLockedFields(shopEvent)
            && (shopEvent.getCurrentCount() > 0 || !shopEvent.isBeforeDeadline(LocalDateTime.now()))) {
            throw new BadRequestException("참가자가 있거나 모집이 끝난 이벤트는 수량, 가격, 마감 시각을 수정할 수 없습니다. (설명과 방문 날짜만 수정할 수 있어요)");
        }
        request.validate(shopEvent);

        shopEvent.updateConditions(request.getCapacity(), request.getFeeAmount(), request.getMaxPerUser(), request.getDeadline());
        if (request.getVisitUntil() != null) {
            shopEvent.updateVisitUntil(request.getVisitUntil());
        }
        Item item = shopEvent.getItem();
        if (request.getItemDescription() != null || request.getItemPrice() != null) {
            item.update(item.getShopId(), item.getCategory(), item.getTitle(),
                request.getItemDescription() != null ? request.getItemDescription() : item.getDescription(),
                request.getItemPrice() != null ? request.getItemPrice() : item.getPrice(),
                null, null, item.getImageUrl());
        }
        return ShopEventResponse.from(shopEvent);
    }

    // 가게 이벤트 정산 보기: 이벤트별 참가비 합계와 취소/환불, 정산 상태 (최근 이벤트 먼저)
    public List<EventSettlementResponse> getSettlement(Long shopId, Long userId) {
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다"));
        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인 가게의 정산만 볼 수 있습니다");
        }
        // 이벤트 id -> [참가중, 손님 취소, 가게 취소(환불)] 수량
        java.util.Map<Long, int[]> sums = new java.util.HashMap<>();
        for (Object[] row : eventParticipationRepository.sumQuantityByShop(shopId)) {
            int[] q = sums.computeIfAbsent((Long) row[0], k -> new int[3]);
            int quantity = ((Number) row[2]).intValue();
            switch ((EventParticipationStatus) row[1]) {
                case JOINED -> q[0] += quantity;
                case CANCELLED -> q[1] += quantity;
                case REFUNDED -> q[2] += quantity;
            }
        }
        return shopEventRepository.getShopEventAll(shopId, null).stream()
            .sorted(java.util.Comparator.comparing(ShopEvent::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
            .map(e -> {
                int[] q = sums.getOrDefault(e.getId(), new int[3]);
                return new EventSettlementResponse(e, q[0], q[1], q[2]);
            }).toList();
    }

    // 가게 이벤트 정산 내역 (정산된 구매 건, 최신순). 달력/오늘·이번 달·저번 달 합계 화면용
    public List<EventSettlementRecordResponse> getSettlementRecords(Long shopId, Long userId) {
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다"));
        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인 가게의 정산만 볼 수 있습니다");
        }
        return eventParticipationRepository.findSettlementRecords(shopId);
    }

    public List<ShopEventResponse> getEventList(Long id, ShopEventStatus status) {
        List<ShopEvent> shopEvents = shopEventRepository.getShopEventAll(id, status);
        return shopEvents.stream().map(ShopEventResponse::from).toList();
    }

    // 로그인한 유저가 이 이벤트를 연 가게의 주인인지 (상세 화면에서 참가 버튼을 막는 용도)
    public boolean isEventOwner(Long id, Long userId) {
        if (userId == null) return false;
        return shopEventRepository.findById(id).map(e -> e.isShop(userId)).orElse(false);
    }

    public ShopEventResponse getEventDetail(Long id) {
        ShopEvent shopEvent = shopEventRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 이벤트입니다"));
        return ShopEventResponse.from(shopEvent);
    }

    @Transactional
    public void cancelShopEvent(Long id, Long userId) {
        //가게id랑 userid 같은지 가게 주인인지 (참가 신청/취소와 동시에 처리되지 않도록 잠금)
         ShopEvent shopEvent = shopEventRepository.findByIdForUpdate(id).orElseThrow(()->{
            throw new NotFoundException("존재하지 않는 이벤트입니다");
        });
        if (!shopEvent.isShop(userId)){
            throw new ForbiddenException("본인 가게이벤트만 삭제할수있습니다");
        }
        // 마감시간 전이면 취소 가능 (정원이 차서 CLOSED 된 이벤트도 포함). 마감 후에는 이미 진행/정산된 이벤트
        if (shopEvent.getStatus() == ShopEventStatus.CANCELLED || !shopEvent.isBeforeDeadline(LocalDateTime.now())){
            throw new ForbiddenException("이벤트가 진행중일떄 삭제 가능합니다");
        }

        // 참가자 전원 참가비 환불
        for (EventParticipation participation : eventParticipationRepository.findJoinedByShopEventId(id)) {
            paymentService.refund(participation.getPaymentId(), shopEvent.getFeeAmount() * participation.getQuantity(), "가게 이벤트 취소");
            participation.refund();
        }
        //상태 업데이트
        shopEvent.updateStatus(ShopEventStatus.CANCELLED);
    }

    // 가게 주인: 이벤트 참가자 명단 (번호표 순, 취소/환불된 참가도 상태와 함께 보여준다)
    public List<EventParticipationResponse> getParticipants(Long id, Long userId) {
        ShopEvent shopEvent = shopEventRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 이벤트입니다"));
        if (!shopEvent.isShop(userId)) {
            throw new ForbiddenException("본인 가게 이벤트의 참가자만 볼 수 있습니다");
        }
        return eventParticipationRepository.findAllByShopEventId(id).stream()
            .map(EventParticipationResponse::new).toList();
    }

    public List<EventParticipationResponse> getEventParticipationList(Long userid) {
    List<EventParticipation> eventParticipationList = eventParticipationRepository.findByUserId(userid);
        return eventParticipationList.stream()
            .map(EventParticipationResponse::new).toList();
    }


    /// ---------shopEvent service

}
