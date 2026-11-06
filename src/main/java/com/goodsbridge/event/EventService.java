package com.goodsbridge.event;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.event.dto.EventParticipationRequest;
import com.goodsbridge.event.dto.EventParticipationResponse;
import com.goodsbridge.event.dto.ShopEventRequest;
import com.goodsbridge.event.dto.ShopEventResponse;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.shop.ShopStatus;
import com.goodsbridge.user.Status;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /// ---------EventParticipation service
    @Transactional
    public EventParticipationResponse saveEventParticipation(Long id, Long userid, EventParticipationRequest request) {
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
        EventParticipation eventParticipation = EventParticipation.builder()
            .shopEvent(shopEvent)
            .user(user)
            .ticketNumber(shopEvent.increaseCurrentCount())
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
        //조회
        ShopEvent shopEvent = shopEventRepository.findById(id).orElseThrow(()->{
            throw new NotFoundException("존재하지 않는 이벤트입니다");}
        );

        User user = userRepository.findById(userid).orElseThrow(()->{
            throw new NotFoundException("존재하지 않는 사용자 입니다");}
        );
        //유저 상태확인
        if (user.getStatus() != Status.ACTIVE){
            throw new ForbiddenException("활성화된 사용자만 참여 가능합니다");
        }
        if (!eventParticipationRepository.existsByShopEvent_IdAndUser_Id(userid, id)) {
            throw new NotFoundException("참가 내역을 찾을 수 없습니다");
        }

        EventParticipation eventParticipation = eventParticipationRepository.findByUserIdAndShopEventId(userid, id).orElseThrow(()->{
            throw new NotFoundException("존재하지 않는 이벤트 참가 내역 입니다");}
        );
        paymentService.refund(eventParticipation.getPaymentId(),shopEvent.getFeeAmount(),"이벤트 참가 취소");
        eventParticipationRepository.cancelByUserIdAndShopEventId(userid,id);
    }


    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void autoCloseShopEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<ShopEvent> shopEvents = shopEventRepository.findByStatusAndDeadlineBefore(ShopEventStatus.OPEN, now);
        for (ShopEvent shopEvent : shopEvents) {
            shopEvent.updateStatus(ShopEventStatus.CLOSED);
        }
    }



    /// ---------EventParticipation service


    /// ---------shopEvent service
    @Transactional
    public ShopEventResponse save(Long id, Long userId, ShopEventRequest request) {
        //shop, item 조회
        Shop shop = shopRepository.findById(id).orElseThrow(()->{
            throw new NotFoundException("존재하지 않는 가게입니다");
        });
        Item item = itemRepository.findById(request.getItemId()).orElseThrow(()->{
            throw new NotFoundException("존재하지 않는 아이템입니다");
        });

        //로그인 유저가 가게 id 맞는지
        if (!shop.getOwnerUserId().equals(userId)){
            throw new ForbiddenException("본인 가게의 이벤트만 생성할수있습니다");
        }
        if (shop.getStatus() != ShopStatus.APPROVED){
            throw new BadRequestException("입점 승인된 가게만 이벤트를 할수있습니다.");
        }
        //아이템이 로그인 유저께
        if (!item.getSellerUserId().equals(userId)){
            throw new ForbiddenException("본인 아이템으로만 이벤트 생성할수있습니다");
        }

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
        //가게id랑 userid 같은지 가게 주인인지
         ShopEvent shopEvent = shopEventRepository.findById(id).orElseThrow(()->{
            throw new NotFoundException("존재하지 않는 이벤트입니다");
        });
        if (!shopEvent.isShop(userId)){
            throw new ForbiddenException("본인 가게이벤트만 삭제할수있습니다");
        }
        //가게이벤트 오픈이였는지
        if (!shopEvent.isOpen()){
            throw new ForbiddenException("이벤트가 진행중일떄 삭제 가능합니다");
        }

        //나중에 로직 생기면 그떄 포트원으로 환불처리
        //상태 업데이트
        shopEvent.updateStatus(ShopEventStatus.CANCELLED);
    }

    public List<EventParticipationResponse> getEventParticipationList(Long userid) {
    List<EventParticipation> eventParticipationList = eventParticipationRepository.findByUserId(userid);
        return eventParticipationList.stream()
            .map(EventParticipationResponse::new).toList();
    }


    /// ---------shopEvent service

}
