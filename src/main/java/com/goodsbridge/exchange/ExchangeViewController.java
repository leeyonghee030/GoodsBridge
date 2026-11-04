package com.goodsbridge.exchange;

import com.goodsbridge._core.util.SecurityUtil;
import com.goodsbridge.exchange.dto.ExchangeItemCreateRequest;
import com.goodsbridge.exchange.dto.ExchangeItemResponse;
import com.goodsbridge.exchange.dto.ExchangeRequestCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Controller
@RequiredArgsConstructor
public class ExchangeViewController {

    private final ExchangeService exchangeService;

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
//    @ResponseBody
//    @PostMapping("/api/exchange-items/{id}/propose")
//    public ResponseEntity<ExchangeRequestRepository> proposeExchange(@PathVariable Long id,
//        @RequestBody ExchangeRequestCreateRequest request) {
//        Long userId = SecurityUtil.getCurrentUserId();
//        request.validate();
//        ExchangeRequestRepository requestRepository=  exchangeService.proposeExchange(id,request,userId);
//        return ResponseEntity.ok().body(requestRepository);
//    }

    //게시글 주인(A) 거절 → B 보증금 100% 환불,
    @PostMapping("/api/exchange-requests/{id}/reject")
    public void rejectExchangeRequest() {

    }

    //제안자(B)가 본인 제안 취소 → B 보증금 100% 환불,
    @PostMapping("/api/exchange-requests/{id}/cancel")
    public void cancelExchangeRequest() {
    }

    //내 교환 현황
    @GetMapping("/api/exchange-requests/me")
    public void getMyExchangeRequests() {
    }










}
