package com.goodsbridge.event;

import com.goodsbridge._core.util.SecurityUtil;
import com.goodsbridge.event.dto.ShopEventRequest;
import com.goodsbridge.event.dto.ShopEventResponse;
import lombok.RequiredArgsConstructor;
import org.hibernate.sql.exec.spi.SecondaryAction;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class EventViewController {

    private final ShopEventService shopEventService;

    //이벤트 생성
    @PostMapping("/api/shops/{id}/events")
    public ResponseEntity<ShopEventResponse> createShopEvent(@PathVariable Long id, @RequestBody ShopEventRequest request)  {
        Long userId = SecurityUtil.getCurrentUserId();
        request.validate();
        ShopEventResponse shopEventResponse = shopEventService.save(id, userId, request);
        return ResponseEntity.status(201).body(shopEventResponse);
    }

    //이벤트 목록
    @GetMapping("/api/events")
    public ResponseEntity<List<ShopEventResponse>> getEventList(@RequestParam(name = "shopId",required = false) Long id,
                                             @RequestParam(name = "status",required = false) ShopEventStatus status) {
        List<ShopEventResponse> shopEventResponseList = shopEventService.getEventList(id,status);
        return ResponseEntity.ok().body(shopEventResponseList);

    }

    //이벤트 상세
    @GetMapping("/api/events/{id}")
    public ResponseEntity<ShopEventResponse> getEventDetail(@PathVariable Long id) {
        ShopEventResponse shopEventResponse = shopEventService.getEventDetail(id);
        return ResponseEntity.ok().body(shopEventResponse);

    }

    //이벤트 삭제 + 환불
    @PostMapping("/api/events/{id}/cancel")
    public ResponseEntity<Void> cancelShopEvent(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        shopEventService.cancelShopEvent(id,userId);
        return ResponseEntity.ok().build();
    }
}
