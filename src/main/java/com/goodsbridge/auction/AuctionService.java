package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService {
    private final AuctionRepository auctionRepository;
    private final ShopRepository shopRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;

    // 경매 개설
    @Transactional
    public AuctionResponse create(Long userId, Long shopId, AuctionRequest request) {
        request.validate(); // 유효성 검사
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("가게를 찾을 수 없습니다."));
        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("가게의 주인만 경매를 열 수 있습니다.");
        }
        Item item = itemRepository.findById(request.getItemId())
            .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다."));

        Auction auction = Auction.builder()
            .shop(shop)
            .item(item)
            .startPrice(request.getStartPrice())
            .deadline(request.getDeadline())
            .depositAmount(request.getDepositAmount())
            .build();
        Auction savedAuction = auctionRepository.save(auction);
        return new AuctionResponse(savedAuction);
    }

    // 경매 목록 조회 + 페이징 처리
    public Page<AuctionResponse> findAll(int page, int size, Status status) {
        int pageIndex = Math.max(0, page - 1);
        int validSize = Math.max(1, Math.min(size, 50));
        Pageable pageable = PageRequest.of(pageIndex, validSize, Sort.by(Sort.Direction.DESC, "id"));

        Page<Auction> auctionPage;
        if (status == null) {
            auctionPage = auctionRepository.findAll(pageable);
        } else {
            auctionPage = auctionRepository.findAllByStatus(status, pageable);
        }
        return auctionPage.map(AuctionResponse::new);
    }

    // 경매 상세 조회
    public AuctionResponse findById(Long id) {
        Auction auction = auctionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));
        return new AuctionResponse(auction);
    }

    // 경매 마감
    @Transactional
    public AuctionResponse close(Long userId, Long id) {
        Auction auction = auctionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        boolean isOwner = auction.getShop().isOwner(userId);
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("경매를 마감할 권한이 없습니다.");
        }

        closeInternal(auction);
        return new AuctionResponse(auction);
    }

    private void closeInternal(Auction auction) {
        if (auction.getStatus() != Status.ACTIVE) {
            throw new BadRequestException("진행 중인 경매만 마감할 수 있습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (auction.getDeadline().isAfter(now)) {
            throw new BadRequestException("아직 마감 시간이 지나지 않았습니다.");
        }
        auction.end();
    }

    // 경매 마감시간 지나면 자동 종료
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void autoCloseAuctions() {
        LocalDateTime now = LocalDateTime.now();
        List<Auction> auctions = auctionRepository.findByStatusAndDeadlineBefore(Status.ACTIVE, now);
        for (Auction auction : auctions) {
            closeInternal(auction);
        }
    }
}
