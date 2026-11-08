package com.goodsbridge.exchange;

import com.goodsbridge._core.util.SecurityUtil;
import com.goodsbridge.exchange.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Controller
@RequiredArgsConstructor
public class ExchangeViewController {

    private final ExchangeService exchangeService;

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.channel-key}")
    private String channelKey;

    //   교환 보증금 정산( 교환 수락, 가게(check-in))
    //교환수락
    @PostMapping("/api/exchange-requests/{id}/accept")
    public ResponseEntity<ExchangeRequestResponse> accept(@PathVariable Long id,
                                                          @RequestBody ExchangeAcceptRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        request.validate();
        ExchangeRequestResponse response = exchangeService.accept(id, userId, request);
        return ResponseEntity.ok().body(response);
    }

    @PatchMapping("/api/shops/{shopId}/exchange-requests/{id}/check-in")
    public ResponseEntity<ExchangeRequestResponse> checkIn(@PathVariable Long id,
                            @RequestBody ExchangeCheckInRequest request){
        Long userId = SecurityUtil.getCurrentUserId();
        request.validate();
        ExchangeRequestResponse response = exchangeService.checkIn(id,userId,request);
        return ResponseEntity.ok().body(response);
    }



    //-------교환 게시글 목록
    @GetMapping("/exchange")
    public String exchangeList(Model model) {
        model.addAttribute("items", exchangeService.getExchangeItemList(null));
        return "exchange/index";
    }

    //교환 게시글 작성 폼
    @GetMapping("/exchange/new")
    public String exchangeCreateForm() {
        return "exchange/create";
    }

    //교환 게시글 상세
    @GetMapping("/exchange/{id}")
    public String exchangeDetail(@PathVariable Long id, Model model) {
        model.addAttribute("item", exchangeService.getExchangeItem(id));
        model.addAttribute("currentUserId", SecurityUtil.getCurrentUserIdOrNull());
        model.addAttribute("portoneStoreId", storeId);
        model.addAttribute("portoneChannelKey", channelKey);
        return "exchange/detail";
    }

    //게시글 주인이 받은 대기중 제안 목록 (수락 화면용)
    @ResponseBody
    @GetMapping("/api/exchange-items/{id}/requests")
    public ResponseEntity<List<ExchangeRequestResponse>> getPendingRequests(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(exchangeService.getPendingRequests(id, userId));
    }

    //--


    //-------교환 게시글 댓글
    @PostMapping("/api/exchange-items/{id}/comments")
    public ResponseEntity<ExchangeCommentResponse> createExchangeComment(@PathVariable(name = "id") Long id,
                                                                         @RequestBody ExchangeCommentRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        request.validate();
        ExchangeCommentResponse response = exchangeService.saveComment(id, userId, request);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/api/exchange-items/{id}/comments")
    public ResponseEntity<List<ExchangeCommentResponse>> getExchangeCommentList(@PathVariable Long id) {
        List<ExchangeCommentResponse> responses = exchangeService.getExchangeCommentList(id);
        return ResponseEntity.ok().body(responses);
    }

    @PatchMapping("/api/exchange-comments/{id}")
    public ResponseEntity<ExchangeCommentResponse> updateComment(@PathVariable(name = "id") Long id,
                                                                 @RequestBody ExchangeCommentRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        request.validate();
        ExchangeCommentResponse response = exchangeService.updateComment(id, userId, request);
        return ResponseEntity.ok().body(response);
    }

    @DeleteMapping("/api/exchange-comments/{id}")
    public ResponseEntity<?> deleteComment(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        exchangeService.deleteComment(id, userId);
        return ResponseEntity.noContent().build();
    }
// -----------------------


    //-------------교환 -------------------
    //교환 게시글 등록
    @ResponseBody
    @PostMapping("/api/exchange-items")
    public ResponseEntity<ExchangeItemResponse> createExchangeItem(@RequestBody ExchangeItemCreateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        request.validate();
        ExchangeItemResponse response = exchangeService.save(request, userId);
        return ResponseEntity.status(201).body(response);
    }

    //탐색
    @ResponseBody
    @GetMapping("/api/exchange-items")
    public ResponseEntity<List<ExchangeItemResponse>> getExchangeItemList(
        @RequestParam(name = "category", required = false) Long category) {
        List<ExchangeItemResponse> itemResponseList = exchangeService.getExchangeItemList(category);
        return ResponseEntity.ok().body(itemResponseList);
    }

    //내 교환 게시글 목록
    @ResponseBody
    @GetMapping("/api/exchange-items/me")
    public ResponseEntity<List<ExchangeItemResponse>> getMyExchangeItems() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<ExchangeItemResponse> itemResponseList = exchangeService.getMyExchangeItems(userId);
        return ResponseEntity.ok().body(itemResponseList);
    }

    //교환 제안 → 제안자(B) 보증금 결제 검증
    @ResponseBody
    @PostMapping("/api/exchange-items/{id}/propose")
    public ResponseEntity<ExchangeRequestResponse> proposeExchange(@PathVariable Long id,
                                                                   @RequestBody ExchangeRequestCreateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        request.validate();
        ExchangeRequestResponse response = exchangeService.proposeExchange(id, request, userId);
        return ResponseEntity.status(201).body(response);
    }

    //게시글 주인(A) 거절 → B 보증금 100% 환불,
    @PostMapping("/api/exchange-requests/{id}/reject")
    public void rejectExchangeRequest(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        exchangeService.rejectExchange(id, userId);
    }

    //제안자(B)가 본인 제안 취소 → B 보증금 100% 환불,
    @PostMapping("/api/exchange-requests/{id}/cancel")
    public void cancelExchangeRequest(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        exchangeService.cancelExchange(id, userId);
    }

    //내 교환 현황
    @GetMapping("/api/exchange-requests/me")
    public ResponseEntity<List<ExchangeRequestResponse>> getMyExchangeRequests() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<ExchangeRequestResponse> exchangeRequestResponses = exchangeService.getMyExchange(userId);
        return ResponseEntity.ok().body(exchangeRequestResponses);
    }


}
