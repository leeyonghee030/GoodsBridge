package com.goodsbridge.event;

import com.goodsbridge._core.util.SecurityUtil;
import com.goodsbridge.event.dto.EventParticipationRequest;
import com.goodsbridge.event.dto.EventParticipationResponse;
import com.goodsbridge.event.dto.ShopEventRequest;
import com.goodsbridge.event.dto.ShopEventResponse;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class EventViewController {

    private final EventService eventService;
    private final ShopRepository shopRepository;

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.channel-key}")
    private String channelKey;

    // TODO(데모 종료 후 제거): 데모 모드에서는 이벤트 참가비 결제창을 건너뛴다 (서버는 DemoPaymentService 가 처리)
    @Value("${app.demo.enabled:false}")
    private boolean skipPayment;

    // 선착순 이벤트 목록 (목록/내 참가 정보는 화면에서 API 로 불러온다)
    @GetMapping("/test/event")
    public String index(Model model) {
        model.addAttribute("portoneStoreId", storeId);
        model.addAttribute("skipPayment", skipPayment);
        model.addAttribute("portoneChannelKey", channelKey);
        model.addAttribute("userId", SecurityUtil.getCurrentUserIdOrNull());
        return "event/index";
    }

    // 선착순 이벤트 상세 (참가/취소는 화면에서 API 로 처리)
    @GetMapping("/test/event/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("event", eventService.getEventDetail(id));
        model.addAttribute("portoneStoreId", storeId);
        model.addAttribute("portoneChannelKey", channelKey);
        model.addAttribute("skipPayment", skipPayment);
        model.addAttribute("userId", SecurityUtil.getCurrentUserIdOrNull());
        return "event/detail";
    }

    // 가게 사장님 마이페이지: 이벤트 관리 (이벤트 상품 등록 + 이벤트 열기, 우리 가게 이벤트/참가자)
    @GetMapping("/shop/events")
    public String shopEvents(Model model) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        List<Shop> shops = userId == null ? List.of()
            : shopRepository.findAllByOwnerUserIdAndDeletedFalse(userId, PageRequest.of(0, 20)).getContent();
        // 가게 사장님이 아니면 개인 마이페이지로 돌려보낸다
        if (shops.isEmpty()) {
            return "redirect:/update";
        }
        model.addAttribute("userId", userId);
        model.addAttribute("shops", shops);
        return "event/shop";
    }

    //  --------EventParticipation
    @PostMapping("/api/events/{id}/join")
    public ResponseEntity<EventParticipationResponse> createEventParticipation(
        @PathVariable Long id, @RequestBody EventParticipationRequest request){
        Long userid = SecurityUtil.getCurrentUserId();
        request.validate();
        EventParticipationResponse response = eventService.saveEventParticipation(id,userid,request);
        return ResponseEntity.status(201).body(response);
    }

    //참가 취소
    @DeleteMapping("/api/events/{id}/join")
    public ResponseEntity<?> cancelEventParticipation(@PathVariable Long id){
        Long userid = SecurityUtil.getCurrentUserId();
        eventService.cancelEventParticipation(id, userid);
        return ResponseEntity.noContent().build();
    }

    // 가게 주인: 이벤트 참가자 명단
    @GetMapping("/api/events/{id}/participations")
    public ResponseEntity<List<EventParticipationResponse>> getParticipants(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(eventService.getParticipants(id, userId));
    }

    @GetMapping("/api/events/me")
    public ResponseEntity<List<EventParticipationResponse>> getEventParticipationList() {
        Long userid = SecurityUtil.getCurrentUserId();
        List<EventParticipationResponse> responses = eventService.getEventParticipationList(userid);
        return ResponseEntity.ok().body(responses);
    }


    //  --------EventParticipation

    //----- ShopEvent
    //이벤트 생성
    @PostMapping("/api/shops/{id}/events")
    public ResponseEntity<ShopEventResponse> createShopEvent(@PathVariable Long id, @RequestBody ShopEventRequest request)  {
        Long userId = SecurityUtil.getCurrentUserId();
        request.validate();
        ShopEventResponse shopEventResponse = eventService.save(id, userId, request);
        return ResponseEntity.status(201).body(shopEventResponse);
    }

    //이벤트 목록
    @GetMapping("/api/events")
    public ResponseEntity<List<ShopEventResponse>> getEventList(@RequestParam(name = "shopId",required = false) Long id,
                                             @RequestParam(name = "status",required = false) ShopEventStatus status) {
        List<ShopEventResponse> shopEventResponseList = eventService.getEventList(id,status);
        return ResponseEntity.ok().body(shopEventResponseList);

    }

    //이벤트 상세
    @GetMapping("/api/events/{id}")
    public ResponseEntity<ShopEventResponse> getEventDetail(@PathVariable Long id) {
        ShopEventResponse shopEventResponse = eventService.getEventDetail(id);
        return ResponseEntity.ok().body(shopEventResponse);

    }

    //이벤트 삭제 + 환불
    @PostMapping("/api/events/{id}/cancel")
    public ResponseEntity<Void> cancelShopEvent(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        eventService.cancelShopEvent(id,userId);
        return ResponseEntity.ok().build();
    }
    // ---------ShopEvent--------
}
