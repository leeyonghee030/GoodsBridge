package com.goodsbridge.auction;

import com.goodsbridge._core.util.SecurityUtil;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

// 경매 화면(Mustache). 데이터는 화면에서 /api/auctions/** 로 불러온다.
// API 는 AuctionViewController / BidViewController 에 있다.
@Controller
@RequiredArgsConstructor
public class AuctionPageController {

    private final ShopRepository shopRepository;

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.channel-key}")
    private String channelKey;

    // TODO(데모 종료 후 제거): 데모 모드에서는 경매 보증금/잔금 결제창을 건너뛴다 (서버는 DemoPaymentService 가 처리)
    @Value("${app.demo.enabled:false}")
    private boolean skipPayment;

    private void common(Model model) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        model.addAttribute("userId", userId);
        model.addAttribute("portoneStoreId", storeId);
        model.addAttribute("portoneChannelKey", channelKey);
        model.addAttribute("skipPayment", skipPayment);
        // 가게 계정은 경매에 입찰할 수 없다 (서버에서도 거절하지만 버튼부터 막는다)
        model.addAttribute("shopAccount", userId != null && shopRepository.existsByOwnerUserIdAndDeletedFalse(userId));
    }

    // 경매 목록
    @GetMapping("/test/auction")
    public String list(Model model) {
        common(model);
        return "auction/index";
    }

    // 경매 상세
    @GetMapping("/test/auction/{id}")
    public String detail(@PathVariable Long id, Model model) {
        common(model);
        model.addAttribute("auctionId", id);
        return "auction/detail";
    }

    // 마이페이지: 내 경매 내역 (입찰중 / 낙찰 / 종료·취소)
    @GetMapping("/my/auctions")
    public String myAuctions(Model model) {
        common(model);
        return "auction/my";
    }

    // 가게 사장님 마이페이지: 경매 관리 (경매 열기 + 우리 가게 경매 목록)
    @GetMapping("/shop/auctions")
    public String shopAuctions(Model model) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        List<Shop> shops = userId == null ? List.of()
            : shopRepository.findAllByOwnerUserIdAndDeletedFalse(userId, PageRequest.of(0, 20)).getContent();
        // 가게 사장님이 아니면 개인 마이페이지로 돌려보낸다
        if (shops.isEmpty()) {
            return "redirect:/update";
        }
        common(model);
        model.addAttribute("shops", shops);
        return "auction/shop";
    }
}
