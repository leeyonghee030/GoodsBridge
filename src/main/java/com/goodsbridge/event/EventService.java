package com.goodsbridge.event;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.event.dto.EventParticipationRequest;
import com.goodsbridge.event.dto.EventParticipationResponse;
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
        //이벤트 중복  체크
        if (eventParticipationRepository.existsByShopEvent_IdAndUser_Id(userid, id)) {
            throw new ForbiddenException("같은 이벤트는 한번만 참여가능합니다");
        }

        //이벤트 인원수 확인
        if (shopEvent.getCurrentCount() >= shopEvent.getCapacity()){
            shopEvent.updateStatus(ShopEventStatus.CLOSED);
            throw new BadRequestException("정원 초과로 이벤트 참가 불가능합니다");
        }
        //결제 확인
        String paymentId = paymentService.verify(request.getPaymentId(), shopEvent.getFeeAmount(), PaymentPurpose.EVENT_FEE, userid);
        // 번호표는 인원수가 아니라 지금까지 발급한 최대 번호 + 1 (취소로 인원이 줄어도 번호가 겹치지 않게)
        int ticketNumber = eventParticipationRepository.findMaxTicketNumber(id) + 1;
        shopEvent.increaseCurrentCount();
        EventParticipation eventParticipation = EventParticipation.builder()
            .shopEvent(shopEvent)
            .user(user)
            .ticketNumber(ticketNumber)
            .paymentId(paymentId)
            .build();

        if (shopEvent.getCurrentCount() >= shopEvent.getCapacity()){
            shopEvent.updateStatus(ShopEventStatus.CLOSED);}

        eventParticipationRepository.save(eventParticipation);
        eventParticipationRepository.flush();
        entityManager.refresh(eventParticipation);
        return new EventParticipationResponse(eventParticipation);
    }

    @Transactional
    public void cancelEventParticipation(Long id, Long userid) {
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
        if (!eventParticipationRepository.existsByShopEvent_IdAndUser_Id(userid, id)) {
            throw new NotFoundException("참가 내역을 찾을 수 없습니다");
        }

        EventParticipation eventParticipation = eventParticipationRepository.findByUserIdAndShopEventId(userid, id).orElseThrow(()->{
            throw new NotFoundException("존재하지 않는 이벤트 참가 내역 입니다");}
        );
        paymentService.refund(eventParticipation.getPaymentId(),shopEvent.getFeeAmount(),"이벤트 참가 취소");
        eventParticipationRepository.cancelByUserIdAndShopEventId(userid,id);
        // 자리 반납 (정원이 차서 마감됐던 이벤트는 다시 모집중)
        shopEvent.decreaseCurrentCount();
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
                    settlementHistoryService.calculate(shopEvent.getShop(), payment, shopEvent.getFeeAmount(), SettlementHistoryType.EVENT_FEE);
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
            .deadline(request.getDeadline())
            .build();
        shopEventRepository.save(shopEvent);
        shopEventRepository.flush();
        entityManager.refresh(shopEvent);
        return ShopEventResponse.from(shopEvent);
    }

    public List<ShopEventResponse> getEventList(Long id, ShopEventStatus status) {
        List<ShopEvent> shopEvents = shopEventRepository.getShopEventAll(id, status);
        return shopEvents.stream().map(ShopEventResponse::from).toList();
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
            paymentService.refund(participation.getPaymentId(), shopEvent.getFeeAmount(), "가게 이벤트 취소");
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
