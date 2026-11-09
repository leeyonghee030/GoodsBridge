package com.goodsbridge.event;

import com.goodsbridge._core.util.SecurityUtil;
import com.goodsbridge.event.dto.EventParticipationRequest;
import com.goodsbridge.event.dto.EventParticipationResponse;
import com.goodsbridge.event.dto.ShopEventRequest;
import com.goodsbridge.event.dto.ShopEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class EventViewController {

    private final EventService eventService;

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.channel-key}")
    private String channelKey;

    @GetMapping("/test/event")
    public String index(Model model) {
        model.addAttribute("events", eventService.getEventList(null, null));
        model.addAttribute("portoneStoreId", storeId);
        model.addAttribute("portoneChannelKey", channelKey);
        model.addAttribute("userId", SecurityUtil.getCurrentUserIdOrNull());
        return "event/index";
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
