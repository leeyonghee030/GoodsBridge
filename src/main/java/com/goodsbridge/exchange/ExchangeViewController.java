package com.goodsbridge.exchange;

import com.goodsbridge._core.util.SecurityUtil;
import com.goodsbridge.exchange.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    // TODO(데모 종료 후 제거): 데모 모드에서는 교환 보증금 결제창을 건너뛴다
    @Value("${app.demo.enabled:false}")
    private boolean skipPayment;

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



    //-------교환 게시글 목록 (첫 페이지만 서버 렌더링, 이후는 무한스크롤로 /api/exchange-items/page 호출)
    @GetMapping("/exchange")
    public String exchangeList(@RequestParam(required = false) Long category,
                                @RequestParam(required = false) String keyword,
                                Model model) {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ExchangeItemResponse> page = exchangeService.getExchangeItemPage(category, keyword, pageable);
        model.addAttribute("items", page.getContent());
        model.addAttribute("totalPages", page.getTotalPages());
        model.addAttribute("selectedCategoryId", category);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("userId", SecurityUtil.getCurrentUserIdOrNull());
        return "exchange/index";
    }

    //교환 게시글 목록 페이징 조회 (무한스크롤)
    @ResponseBody
    @GetMapping("/api/exchange-items/page")
    public ResponseEntity<Page<ExchangeItemResponse>> getExchangeItemPage(
        @RequestParam(required = false) Long category,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(exchangeService.getExchangeItemPage(category, keyword, pageable));
    }

    //교환 게시글 작성 폼
    @GetMapping("/exchange/new")
    public String exchangeCreateForm(Model model) {
        model.addAttribute("userId", SecurityUtil.getCurrentUserIdOrNull());
        return "exchange/create";
    }

    //교환 게시글 상세
    @GetMapping("/exchange/{id}")
    public String exchangeDetail(@PathVariable Long id, Model model) {
        model.addAttribute("item", exchangeService.getExchangeItem(id));
        Long currentUserId = SecurityUtil.getCurrentUserIdOrNull();
        model.addAttribute("currentUserId", currentUserId);
        model.addAttribute("userId", currentUserId);
        model.addAttribute("portoneStoreId", storeId);
        model.addAttribute("portoneChannelKey", channelKey);
        model.addAttribute("skipPayment", skipPayment);
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

    //교환 게시글 수정 (원하는 것, 교환장소)
    @ResponseBody
    @PatchMapping("/api/exchange-items/{id}")
    public ResponseEntity<ExchangeItemResponse> updateExchangeItem(@PathVariable Long id,
                                                                   @RequestBody ExchangeItemUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        request.validate();
        return ResponseEntity.ok(exchangeService.updateExchangeItem(id, userId, request.getWantDescription(), request.getShopId()));
    }

    //교환 게시글 삭제
    @ResponseBody
    @DeleteMapping("/api/exchange-items/{id}")
    public ResponseEntity<Void> deleteExchangeItem(@PathVariable Long id) {
        exchangeService.deleteExchangeItem(id, SecurityUtil.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    //마이페이지 교환 내역 화면 (내 교환글 / 보낸 신청 / 받은 신청)
    @GetMapping("/my/exchange")
    public String myExchange(Model model) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        // 가게 사장님은 개인 교환 내역 대신 가게 교환 관리 화면을 쓴다 (데모에서 가게로 전환해도 같은 주소에서 바로 맞는 화면으로)
        if (userId != null && exchangeService.isShopOwner(userId)) {
            return "redirect:/shop/exchange";
        }
        model.addAttribute("userId", userId);
        model.addAttribute("portoneStoreId", storeId);
        model.addAttribute("portoneChannelKey", channelKey);
        model.addAttribute("skipPayment", skipPayment);
        return "exchange/my";
    }

    //결제창을 띄우기 전에 신청 가능한지 미리 확인 (불가능하면 400/403/404 + 사유)
    @ResponseBody
    @PostMapping("/api/exchange-items/{id}/propose/check")
    public ResponseEntity<Void> checkProposal(@PathVariable Long id, @RequestBody ExchangeProposeCheckRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        request.validate();
        exchangeService.checkProposal(id, request.getItemBId(), userId);
        return ResponseEntity.noContent().build();
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
    @ResponseBody
    @PostMapping("/api/exchange-requests/{id}/reject")
    public void rejectExchangeRequest(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        exchangeService.rejectExchange(id, userId);
    }

    //제안자(B)가 본인 제안 취소 → B 보증금 100% 환불,
    @ResponseBody
    @PostMapping("/api/exchange-requests/{id}/cancel")
    public void cancelExchangeRequest(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        exchangeService.cancelExchange(id, userId);
    }

    //가게 사장님: 내 가게의 교환 목록 (교환번호로 찾아 방문 체크)
    @ResponseBody
    @GetMapping("/api/exchange-requests/shop")
    public ResponseEntity<ShopExchangeListResponse> getShopExchanges(
        @RequestParam(defaultValue = "waiting") String tab,
        @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(exchangeService.getShopExchanges(SecurityUtil.getCurrentUserId(), tab, keyword));
    }

    //가게 사장님: 오늘 방문 예정 + 교환 수수료 수익 요약
    @ResponseBody
    @GetMapping("/api/exchange-requests/shop/summary")
    public ResponseEntity<ShopExchangeSummaryResponse> getShopSummary() {
        return ResponseEntity.ok(exchangeService.getShopSummary(SecurityUtil.getCurrentUserId()));
    }

    //가게 사장님 교환 관리 화면
    @GetMapping("/shop/exchange")
    public String shopExchange(Model model) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        // 가게 사장님이 아니면(다른 계정으로 전환했거나 로그아웃 상태) 개인 교환 내역으로 돌려보낸다
        if (userId == null || !exchangeService.isShopOwner(userId)) {
            return "redirect:/my/exchange";
        }
        model.addAttribute("userId", userId);
        return "exchange/shop";
    }

    //내 교환 현황
    @GetMapping("/api/exchange-requests/me")
    public ResponseEntity<List<ExchangeRequestResponse>> getMyExchangeRequests() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<ExchangeRequestResponse> exchangeRequestResponses = exchangeService.getMyExchange(userId);
        return ResponseEntity.ok().body(exchangeRequestResponses);
    }


}
