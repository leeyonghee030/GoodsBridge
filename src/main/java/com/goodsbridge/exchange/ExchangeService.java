package com.goodsbridge.exchange;


import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.exchange.dto.*;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.notification.Notification;
import com.goodsbridge.notification.NotificationEvent;
import com.goodsbridge.notification.NotificationService;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.settlement.SettlementHistoryService;
import com.goodsbridge.settlement.SettlementHistoryType;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.shop.ShopStatus;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.Status;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeService {

    private final ExchangeRequestRepository exchangeRequestRepository;
    private final ExchangeItemRepository exchangeItemRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ItemRepository itemRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final PaymentRepository paymentRepository;
    private final EntityManager entityManager;
    private final ExchangeCommentRepository exchangeCommentRepository;
    private final SettlementHistoryService settlementHistoryService;
    private final ApplicationEventPublisher publisher;

    //교환 댓글 service--------------
    @Transactional
    public ExchangeCommentResponse saveComment(Long id, Long userId, ExchangeCommentRequest request) {
        //조회하기
        ExchangeItem exchangeItem = exchangeItemRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 교환 게시글 입니다."));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자 입니다."));
        if (user.getStatus() != Status.ACTIVE) {
            throw new ForbiddenException("활성화된 유저만 댓글 작성 가능합니다");
        }

        //entity만들기
        ExchangeComment exchangeComment = ExchangeComment.builder()
            .exchangeItem(exchangeItem)
            .user(user)
            .content(request.getContent())
            .build();
        exchangeCommentRepository.save(exchangeComment);
        exchangeCommentRepository.flush();
        entityManager.refresh(exchangeComment);
        return new ExchangeCommentResponse(exchangeComment);
    }

    public List<ExchangeCommentResponse> getExchangeCommentList(Long id) {
        List<ExchangeComment> exchangeComments = exchangeCommentRepository.getCommentList(id);
        return exchangeComments.stream().map(ExchangeCommentResponse::new).toList();
    }

    @Transactional
    public ExchangeCommentResponse updateComment(Long id, Long userId, ExchangeCommentRequest request) {
        ExchangeComment exchangeComment = exchangeCommentRepository.findById(id)
            .orElseThrow(() -> {
                throw new NotFoundException("존재하지 않는 댓글입니다");
            });
        if (!exchangeComment.getUser().getId().equals(userId)) {
            throw new ForbiddenException("작성자만 수정 할수있습니다");
        }
        if (exchangeComment.getUser().getStatus() != Status.ACTIVE) {
            throw new ForbiddenException("활성화된 유저만 댓글 작성 가능합니다");
        }
        exchangeComment.updateContent(request.getContent());
        return new ExchangeCommentResponse(exchangeComment);
    }

    @Transactional
    public void deleteComment(Long id, Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자 입니다."));

        ExchangeComment exchangeComment = exchangeCommentRepository.findById(id)
            .orElseThrow(() -> {
                throw new NotFoundException("존재하지 않는 댓글입니다");
            });

        if (user.getStatus() != Status.ACTIVE) {
            throw new ForbiddenException("활성화된 유저만 댓글 삭제 가능합니다");
        }

        if (!exchangeComment.getUser().getId().equals(userId) && user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("작성자 또는 관리자만 삭제할수있습니다");
        }
        exchangeCommentRepository.delete(exchangeComment);
    }


    //------------------

    //---- 교환 service
    @Transactional
    public ExchangeItemResponse save(ExchangeItemCreateRequest request, Long userId) {
        //게시글 생성
        //각각의 Repository di해서
        //여기서 user,shop,item Repository로 id 값을 조회해서 값가지고와서 값넣기

        // 가게 계정은 교환 게시글을 올릴 수 없다 (교환은 손님끼리, 가게는 중개만)
        if (isShopOwner(userId)) {
            throw new ForbiddenException("가게 계정은 교환에 참여할 수 없습니다.");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자입니다."));
        Shop shop = shopRepository.findById(request.getShopId())
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));
        if (shop.getStatus() != ShopStatus.APPROVED) {
            throw new BadRequestException("입점 승인된 가게만 교환장소로 지정할 수 있습니다.");
        }
        Item item = itemRepository.findById(request.getItemId())
            .orElseThrow(() -> new NotFoundException("존재하지 않는 상품입니다."));
        if (!item.getSellerUserId().equals(userId)) {
            throw new ForbiddenException("본인 소유의 상품으로만 게시글을 등록할 수 있습니다.");
        }

        ExchangeItem exchangeItem = ExchangeItem.builder()
            .user(user)
            .item(item)
            .shop(shop)
            .wantDescription(request.getWantDescription())
            .build();
        exchangeItemRepository.save(exchangeItem);
        exchangeItemRepository.flush();
        entityManager.refresh(exchangeItem);
        return ExchangeItemResponse.from(exchangeItem);
    }

    public List<ExchangeItemResponse> getExchangeItemList(Long category) {
        return getExchangeItemList(category, null);
    }

    public List<ExchangeItemResponse> getExchangeItemList(Long category, String keyword) {
        List<ExchangeItem> items = exchangeItemRepository.search(category, (keyword == null || keyword.isBlank()) ? null : keyword.trim());
        java.util.Map<Long, Long> requestCounts = requestCountsByExchangeItem(items);
        return items.stream()
            .map(item -> ExchangeItemResponse.from(item, requestCounts.get(item.getId())))
            .toList();
    }

    // 무한스크롤용 페이징 조회
    public org.springframework.data.domain.Page<ExchangeItemResponse> getExchangeItemPage(
        Long category, String keyword, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<ExchangeItem> page = exchangeItemRepository
            .search(category, (keyword == null || keyword.isBlank()) ? null : keyword.trim(), pageable);
        java.util.Map<Long, Long> requestCounts = requestCountsByExchangeItem(page.getContent());
        return page.map(item -> ExchangeItemResponse.from(item, requestCounts.get(item.getId())));
    }

    // 교환 게시글 id별 제안받은 횟수 (인기순 정렬용)
    private java.util.Map<Long, Long> requestCountsByExchangeItem(List<ExchangeItem> items) {
        List<Long> ids = items.stream().map(ExchangeItem::getId).toList();
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        return exchangeRequestRepository.countGroupedByExchangeItemId(ids).stream()
            .collect(java.util.stream.Collectors.toMap(
                ExchangeRequestRepository.ExchangeItemRequestCountView::getExchangeItemId,
                ExchangeRequestRepository.ExchangeItemRequestCountView::getRequestCount));
    }

    //내 게시글 목록
    public List<ExchangeItemResponse> getMyExchangeItems(Long userId) {
        return exchangeItemRepository.findByUser_Id(userId).stream()
            .map(ExchangeItemResponse::from).toList();
    }

    //교환 게시글 수정 (원하는 것, 교환 장소) - 주인만, 모집중일 때만
    @Transactional
    public ExchangeItemResponse updateExchangeItem(Long id, Long userId, String wantDescription, Long shopId) {
        ExchangeItem exchangeItem = exchangeItemRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 교환 게시글입니다."));
        if (!exchangeItem.isOwner(userId)) {
            throw new ForbiddenException("게시글 주인만 수정할 수 있습니다.");
        }
        if (!exchangeItem.isOpen()) {
            throw new BadRequestException("모집중인 게시글만 수정할 수 있습니다.");
        }
        if (wantDescription == null || wantDescription.isBlank()) {
            throw new BadRequestException("원하는 것을 적어주세요.");
        }
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));
        if (shop.getStatus() != ShopStatus.APPROVED) {
            throw new BadRequestException("입점 승인된 가게만 교환장소로 지정할 수 있습니다.");
        }
        exchangeItem.update(wantDescription.trim(), shop);
        return ExchangeItemResponse.from(exchangeItem);
    }

    //교환 게시글 삭제 - 주인만, 모집중이고 대기 중인 신청이 없을 때만 (올린 상품 자체는 내 아이템에 그대로 남는다)
    @Transactional
    public void deleteExchangeItem(Long id, Long userId) {
        ExchangeItem exchangeItem = exchangeItemRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 교환 게시글입니다."));
        if (!exchangeItem.isOwner(userId)) {
            throw new ForbiddenException("게시글 주인만 삭제할 수 있습니다.");
        }
        if (!exchangeItem.isOpen()) {
            throw new BadRequestException("모집중인 게시글만 삭제할 수 있습니다.");
        }
        if (!exchangeRequestRepository.findActiveRequestsByItem(exchangeItem).isEmpty()) {
            throw new BadRequestException("대기 중인 교환 신청이 있어요. 먼저 거절하거나 처리한 뒤 삭제해주세요.");
        }
        exchangeCommentRepository.deleteByExchangeItemId(id);
        exchangeRequestRepository.deleteByExchangeItemId(id);
        exchangeItemRepository.deleteById(id);
    }

    //교환 게시글 상세
    public ExchangeItemResponse getExchangeItem(Long id) {
        ExchangeItem exchangeItem = exchangeItemRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 교환 게시글입니다."));
        return ExchangeItemResponse.from(exchangeItem);
    }

    //게시글 주인(A)이 받은 대기중인 제안 목록 (수락 화면용)
    public List<ExchangeRequestResponse> getPendingRequests(Long exchangeItemId, Long userId) {
        ExchangeItem exchangeItem = exchangeItemRepository.findById(exchangeItemId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 교환 게시글입니다."));
        if (!exchangeItem.isOwner(userId)) {
            throw new ForbiddenException("게시글 주인만 조회할 수 있습니다.");
        }
        return exchangeRequestRepository.findActiveRequestsByItem(exchangeItem).stream()
            .map(ExchangeRequestResponse::from).toList();
    }

    // 제안 대상 검증 결과 (제안/사전확인에서 공통 사용)
    private record ProposalTarget(Item item, ExchangeItem exchangeItem) {}

    // 결제창을 띄우기 전에 클라이언트가 먼저 호출해서, 결제 후 신청이 거절되는 일이 없게 한다.
    // propose 와 같은 검증을 쓰기 때문에 두 곳의 기준이 어긋나지 않는다.
    public void checkProposal(Long exchangeItemId, Long itemBId, Long userId) {
        validateProposal(exchangeItemId, itemBId, userId);
    }

    private ProposalTarget validateProposal(Long exchangeItemId, Long itemBId, Long userId) {
        // 가게 계정은 교환을 신청할 수 없다
        if (isShopOwner(userId)) {
            throw new ForbiddenException("가게 계정은 교환에 참여할 수 없습니다.");
        }
        Item item = itemRepository.findById(itemBId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 상품입니다."));
        //1. 로그인한사람이랑 아이템 주인 같은지
        if (!item.getSellerUserId().equals(userId)) {
            throw new ForbiddenException("본인 소유의 상품으로만 제안할 수 있습니다.");
        }
        //2. 게시글 주인이랑 같은사람아닌지
        ExchangeItem exchangeItem = exchangeItemRepository.findById(exchangeItemId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 교환 게시글입니다."));
        if (exchangeItem.getUser().getId().equals(userId)) {
            throw new ForbiddenException("자기 자신의 게시글에는 제안할 수 없습니다.");
        }
        //3. 아직 교환신정 가능한지
        if (!exchangeItem.isOpen()) {
            throw new BadRequestException("이미 매칭되었거나 마감된 게시글입니다.");
        }
        //4. 판매중인(삭제/교환완료되지 않은) 상품만 제안 가능
        if (item.isDeleted() || item.getStatus() != ItemStatus.ON_SALE) {
            throw new BadRequestException("판매 중인 상품만 교환 제안에 쓸 수 있습니다.");
        }
        //5. 이 아이템이 다른 PENDING/CONFIRMED 제안에 이미 쓰이고 있으면 중복 제안 불가 (한 게시글에 서로 다른 상품으로 여러 번 신청하는 것은 가능)
        List<ExchangeRequestStatus> statuses = new ArrayList<>();
        statuses.add(ExchangeRequestStatus.PENDING);
        statuses.add(ExchangeRequestStatus.CONFIRMED);
        if (exchangeRequestRepository.existsByItemBAndStatusIn(itemBId, statuses)) {
            throw new BadRequestException("이미 다른 교환 신청에 쓰이고 있는 상품입니다.");
        }
        return new ProposalTarget(item, exchangeItem);
    }

    //교환 신청
    @Transactional
    public ExchangeRequestResponse proposeExchange(Long exchangeItemId, ExchangeRequestCreateRequest request, Long userId) {
        ProposalTarget target = validateProposal(exchangeItemId, request.getItemBId(), userId);
        Item item = target.item();
        ExchangeItem exchangeItem = target.exchangeItem();
        //5. 보증금(아이템가의 20%, 최소 2000원) 계산 후 결제 검증
        int depositAmount = Math.max(Math.round(item.getPrice() * 0.2f), 2000);
        String paymentId = paymentService.verify(request.getPaymentId(), depositAmount, PaymentPurpose.EXCHANGE_DEPOSIT, userId);
        //6. 결제되면 신청 만들어주기
        ExchangeRequest exchangeRequest = ExchangeRequest.builder()
            .exchangeItemA(exchangeItem)
            .itemB(item)
            .message(request.getMessage())
            .paymentIdB(paymentId)
            .shop(exchangeItem.getShop())
            .depositAmountB(depositAmount)
            .build();
        exchangeRequestRepository.save(exchangeRequest);
        exchangeRequestRepository.flush();
        entityManager.refresh(exchangeRequest);

        publisher.publishEvent(
            new NotificationEvent(
                exchangeItem.getUser().getId(),
                Notification.Type.EXCHANGE_REQUEST,
                "[교환신청]",
                item.getTitle() + "에 교환 신청이 들어왔습니다",
                Notification.ReferenceType.EXCHANGE,
                exchangeRequest.getId()
            )
        );

        return ExchangeRequestResponse.from(exchangeRequest);
    }


    @Transactional
    public void rejectExchange(Long exchangeRequestsId, Long userId) {
        ExchangeRequest exchangeRequest = exchangeRequestRepository.findById(exchangeRequestsId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 교환 제안입니다."));

        //1. 유저가 게시글 만든사람이 맞는가
        if (!exchangeRequest.isOwner(userId)) {
            throw new ForbiddenException("게시글 주인만 거절할 수 있습니다.");
        }
        //2. 지금 교환 대기중인 상품이 맞는가
        if (!exchangeRequest.isPending()) {
            throw new BadRequestException("대기중인 제안만 거절할 수 있습니다.");
        }
        //3. B 보증금 100% 환불
        paymentService.refund(exchangeRequest.getPaymentIdB(), exchangeRequest.getDepositAmountB(), "교환 제안 거절");
        //4. 상태 변경
        exchangeRequest.updateStatus(ExchangeRequestStatus.REJECTED);

        publisher.publishEvent(
            new NotificationEvent(
                exchangeRequest.getItemB().getSellerUserId(),
                Notification.Type.EXCHANGE_REJECTED,
                "[교환거절]",
                exchangeRequest.getExchangeItemA().getItem().getTitle() +
                    " 교환신청이 거절되었습니다. 보증금은 전액 환불됩니다",
                Notification.ReferenceType.EXCHANGE,
                exchangeRequest.getId()
            )
        );
    }

    @Transactional
    public void cancelExchange(Long exchangeRequestsId, Long userId) {
        //게시글 존재하는지
        ExchangeRequest exchangeRequest = exchangeRequestRepository.findById(exchangeRequestsId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 교환 제안입니다"));
        //유저가 교환신청한사람맞는지?
        if (!exchangeRequest.isProposer(userId)) {
            throw new ForbiddenException("교환 신청자만 교환 취소 할 수있습니다");
        }
        //상태가 대기중 맞는지
        if (!exchangeRequest.isPending()) {
            throw new BadRequestException("대기중인 제안만 취소할 수 있습니다");
        }
        // 보증금환불
        paymentService.refund(exchangeRequest.getPaymentIdB(), exchangeRequest.getDepositAmountB(), "교환 취소");
        //상태변경
        exchangeRequest.updateStatus(ExchangeRequestStatus.CANCELLED);

        publisher.publishEvent(
            new NotificationEvent(
                exchangeRequest.getExchangeItemA().getUser().getId(),
                Notification.Type.EXCHANGE_CANCELLED,
                "[교환취소]",
                exchangeRequest.getExchangeItemA().getItem().getTitle() +
                    " 교환신청이 취소되었습니다",
                Notification.ReferenceType.EXCHANGE,
                exchangeRequest.getId()
            )
        );
    }

    public List<ExchangeRequestResponse> getMyExchange(Long userId) {
        List<ExchangeRequest> exchangeRequestList = exchangeRequestRepository.findByExchangeItemA_User_IdOrItemB_SellerUserId(userId);
        return exchangeRequestList.stream()
            .map(ExchangeRequestResponse::from).toList();
    }

    /// ------------- 교환수락 ----
    @Transactional
    public ExchangeRequestResponse accept(Long id, Long userId, ExchangeAcceptRequest request) {
        ExchangeRequest exchangeRequest = exchangeRequestRepository.findById(id).orElseThrow(() -> {
            throw new NotFoundException("교환 예약이 없습니다");
        });
        if (!exchangeRequest.isOwner(userId)) {
            throw new ForbiddenException("본인 교환건만 수락할수있습니다");
        }
        //2. 대기중인 제안만 수락 가능
        if (!exchangeRequest.isPending()) {
            throw new BadRequestException("대기중인 제안만 수락할 수 있습니다");
        }
        Integer amountA = Math.max(Math.round(exchangeRequest.getExchangeItemA().getItem().getPrice() * 0.2f), 2000);
        // a 금액 저장
        exchangeRequest.updateDepositAmountA(amountA);
        //결제했는지 검사
        String paymentId = paymentService.verify(request.getPaymentId(), exchangeRequest.getDepositAmountA(), PaymentPurpose.EXCHANGE_DEPOSIT, userId);
        // a payment id 저장
        exchangeRequest.updatePaymentIdA(paymentId);
        exchangeRequest.getExchangeItemA().updateStatus(ExchangeItemStatus.MATCHED);
        exchangeRequest.getItemB().updateStatus(ItemStatus.SOLD);
        exchangeRequest.getExchangeItemA().getItem().updateStatus(ItemStatus.SOLD);
        exchangeRequest.updateStatus(ExchangeRequestStatus.CONFIRMED);

        publisher.publishEvent(
            new NotificationEvent(
                exchangeRequest.getItemB().getSellerUserId(),
                Notification.Type.EXCHANGE_APPROVED,
                "[교환 신청 승인]",
                "신청한 교환이 승인되었습니다.",
                Notification.ReferenceType.EXCHANGE,
                exchangeRequest.getId()
            )
        );

        // a 한테 교환신청한 다른 b 들 환불
        List<ExchangeRequest> exchangeRequestList = exchangeRequestRepository.findActiveRequestsByItem(exchangeRequest.getExchangeItemA());
        for (ExchangeRequest e : exchangeRequestList) {
            paymentService.refund(e.getPaymentIdB(), e.getDepositAmountB(), "교환 취소");
            //상태변경
            e.updateStatus(ExchangeRequestStatus.CANCELLED);

            publisher.publishEvent(
                new NotificationEvent(
                    e.getItemB().getSellerUserId(),
                    Notification.Type.EXCHANGE_CANCELLED,
                    "[교환취소]",
                    exchangeRequest.getExchangeItemA().getItem().getTitle() +
                        " 교환신청이 취소되었습니다",
                    Notification.ReferenceType.EXCHANGE,
                    e.getId()
                )
            );
        }
        exchangeRequest.updateDeadlineForVisit();

        publisher.publishEvent(
            new NotificationEvent(
                exchangeRequest.getShop().getOwnerUserId(),
                Notification.Type.EXCHANGE_BOOKED,
                "[교환예약]",
                exchangeRequest.getDeadline() +
                    "까지 교환 예약 일정이 생겼습니다",
                Notification.ReferenceType.EXCHANGE,
                exchangeRequest.getId()
            )
        );

        return ExchangeRequestResponse.from(exchangeRequest);
    }

    // 가게 사장님 화면용 교환 목록 (내 가게 것만, 탭별 + 검색, 최신 100건까지)
    private static final List<ExchangeRequestStatus> SHOP_WAITING = List.of(ExchangeRequestStatus.CONFIRMED);
    private static final List<ExchangeRequestStatus> SHOP_DONE = List.of(
        ExchangeRequestStatus.COMPLETED, ExchangeRequestStatus.EXPIRED_PARTIAL, ExchangeRequestStatus.EXPIRED_BOTH);

    // 내 가게(삭제되지 않은)가 있는 사장님인지
    public boolean isShopOwner(Long userId) {
        return shopRepository.existsByOwnerUserIdAndDeletedFalse(userId);
    }

    public ShopExchangeListResponse getShopExchanges(Long userId, String tab, String keyword) {
        if (!isShopOwner(userId)) {
            throw new ForbiddenException("가게 사장님만 이용할 수 있습니다.");
        }
        List<ExchangeRequestStatus> statuses = "done".equals(tab) ? SHOP_DONE : SHOP_WAITING;
        String kw = keyword == null ? "" : keyword.trim().replaceFirst("^#", "").toLowerCase();
        int limit = ShopExchangeListResponse.LIMIT;
        // 한 건 더 가져와서 100건을 넘는지 판단한다
        List<ExchangeRequest> list = exchangeRequestRepository.searchShopExchanges(
            userId, statuses, kw, org.springframework.data.domain.PageRequest.of(0, limit + 1));
        boolean limited = list.size() > limit;
        if (limited) list = list.subList(0, limit);

        java.util.Map<Long, String> nicknames = new java.util.HashMap<>();
        userRepository.findAllById(list.stream().map(e -> e.getItemB().getSellerUserId()).distinct().toList())
            .forEach(u -> nicknames.put(u.getId(), u.getNickname()));
        List<ShopExchangeResponse> items = list.stream()
            .map(e -> ShopExchangeResponse.from(e, nicknames.get(e.getItemB().getSellerUserId())))
            .toList();
        return new ShopExchangeListResponse(
            exchangeRequestRepository.countShopExchanges(userId, SHOP_WAITING),
            exchangeRequestRepository.countShopExchanges(userId, SHOP_DONE),
            items, limited);
    }

    // 가게 교환 관리 상단: 오늘 방문 예정 + 교환 수수료 수익(오늘/이번 달/누적)
    public ShopExchangeSummaryResponse getShopSummary(Long userId) {
        if (!isShopOwner(userId)) {
            throw new ForbiddenException("가게 사장님만 이용할 수 있습니다.");
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        List<ExchangeRequest> meetings = exchangeRequestRepository.findShopMeetingsBetween(
            userId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        java.util.Map<Long, String> nicknames = new java.util.HashMap<>();
        userRepository.findAllById(meetings.stream().map(e -> e.getItemB().getSellerUserId()).distinct().toList())
            .forEach(u -> nicknames.put(u.getId(), u.getNickname()));
        List<ShopExchangeResponse> todayItems = meetings.stream()
            .map(e -> ShopExchangeResponse.from(e, nicknames.get(e.getItemB().getSellerUserId())))
            .toList();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        return new ShopExchangeSummaryResponse(todayItems,
            sumExchangeFee(userId, today.atStartOfDay(), null),
            sumExchangeFee(userId, monthStart, null),
            sumExchangeFee(userId, monthStart.minusMonths(1), monthStart),
            monthFeeRecords(userId, monthStart));
    }

    // 이번 달 수수료 정산 내역: 정산 기록(사람별)을 교환 1건 기준으로 묶어서 최신순으로 (최대 200건)
    private List<ShopExchangeSummaryResponse.FeeRecord> monthFeeRecords(Long ownerUserId, LocalDateTime from) {
        List<Object[]> rows = entityManager.createQuery("""
            select s.createdAt, s.amount, s.type, e.id, pu.nickname
            from SettlementHistory s join s.payment p join p.user pu, ExchangeRequest e
            where s.shop.ownerUserId = :owner and s.createdAt >= :from
            and s.type in (com.goodsbridge.settlement.SettlementHistoryType.EXCHANGE_FEE,
                           com.goodsbridge.settlement.SettlementHistoryType.NO_SHOW_FEE)
            and (e.paymentIdA = p.paymentId or e.paymentIdB = p.paymentId)
            order by s.createdAt desc, s.id desc
            """, Object[].class)
            .setParameter("owner", ownerUserId).setParameter("from", from).getResultList();

        java.util.Map<Long, List<Object[]>> byExchange = new java.util.LinkedHashMap<>();
        for (Object[] r : rows) byExchange.computeIfAbsent((Long) r[3], k -> new ArrayList<>()).add(r);
        if (byExchange.isEmpty()) return List.of();

        java.util.Map<Long, ExchangeRequest> exchanges = new java.util.HashMap<>();
        exchangeRequestRepository.findAllById(byExchange.keySet()).forEach(e -> exchanges.put(e.getId(), e));
        java.util.Map<Long, String> nicknames = new java.util.HashMap<>();
        userRepository.findAllById(exchanges.values().stream().map(e -> e.getItemB().getSellerUserId()).distinct().toList())
            .forEach(u -> nicknames.put(u.getId(), u.getNickname()));

        List<ShopExchangeSummaryResponse.FeeRecord> records = new ArrayList<>();
        for (var entry : byExchange.entrySet()) {
            ExchangeRequest e = exchanges.get(entry.getKey());
            if (e == null) continue;
            List<Object[]> group = entry.getValue();
            long total = 0;
            List<ShopExchangeSummaryResponse.Part> parts = new ArrayList<>();
            for (Object[] r : group) {
                long amount = ((Number) r[1]).longValue();
                total += amount;
                parts.add(new ShopExchangeSummaryResponse.Part((String) r[4], amount));
            }
            String kind = group.get(0)[2] == com.goodsbridge.settlement.SettlementHistoryType.EXCHANGE_FEE ? "COMPLETED" : "NO_SHOW";
            records.add(new ShopExchangeSummaryResponse.FeeRecord(
                e.getId(), (LocalDateTime) group.get(0)[0], kind, total,
                e.getExchangeItemA().getUser().getNickname(), nicknames.get(e.getItemB().getSellerUserId()), parts));
            if (records.size() >= 200) break;
        }
        return records;
    }

    // 교환으로 가게가 가져간 수수료(정상 교환 수수료 + 불참 차감액) 합계
    private long sumExchangeFee(Long ownerUserId, LocalDateTime from, LocalDateTime to) {
        Long sum = entityManager.createQuery("""
            select coalesce(sum(s.amount), 0) from SettlementHistory s
            where s.shop.ownerUserId = :owner and s.createdAt >= :from and s.createdAt < :to
            and s.type in (com.goodsbridge.settlement.SettlementHistoryType.EXCHANGE_FEE,
                           com.goodsbridge.settlement.SettlementHistoryType.NO_SHOW_FEE)
            """, Long.class)
            .setParameter("owner", ownerUserId).setParameter("from", from)
            .setParameter("to", to == null ? LocalDateTime.of(2999, 1, 1, 0, 0) : to).getSingleResult();
        return sum == null ? 0 : sum;
    }

    //    --------체크인
    @Transactional
    public ExchangeRequestResponse checkIn(Long id, Long userId, ExchangeCheckInRequest request) {

        ExchangeRequest exchangeRequest = exchangeRequestRepository.findById(id).orElseThrow(() -> {
            throw new NotFoundException("교환 신청내역을 찾을수없습니다");
        });
        if (!exchangeRequest.getShop().isOwner(userId)) {
            throw new ForbiddenException("운영하는 가게만 수정할수있습니다");
        }
        if (exchangeRequest.getStatus() != ExchangeRequestStatus.CONFIRMED) {
            throw new ForbiddenException("확정된 교환건만 수정할수있습니다");
        }

        if (request.getUserAShowed() != null) {
            exchangeRequest.checkInA(request.getUserAShowed());
        }
        if (request.getUserBShowed() != null) {
            exchangeRequest.checkInB(request.getUserBShowed());
        }
        // 둘 다 체크돼도 바로 정산하지 않는다. 실수로 눌렀을 때 되돌릴 수 있게 유예 시간 뒤에 정산한다.
        exchangeRequest.refreshBothChecked();
        return ExchangeRequestResponse.from(exchangeRequest);
    }

    // 가게가 두 사람 모두 체크한 뒤 유예 시간(5분)이 지나면 교환 완료 처리 + 보증금 정산
    public static final int COMPLETE_GRACE_MINUTES = 5;

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void completeAfterGrace() {
        for (ExchangeRequest e : exchangeRequestRepository.findReadyToComplete(LocalDateTime.now().minusMinutes(COMPLETE_GRACE_MINUTES))) {
            e.updateStatus(ExchangeRequestStatus.COMPLETED);
            settle(e);
        }
    }

    //---- 데스라인 스케줄러
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void autoCloseExchangeEvent() {
        List<ExchangeRequest> exchangeRequestList = exchangeRequestRepository.findByStatusAndDeadlineBefore();
        for (ExchangeRequest e : exchangeRequestList) {
            if (e.getStatus() == ExchangeRequestStatus.PENDING) {
                paymentService.refund(e.getPaymentIdB(), e.getDepositAmountB()
                    , "제안 응답 기한 초과");
                e.updateStatus(ExchangeRequestStatus.PROPOSAL_EXPIRED);
            } else {
                e.isUserShowed();
                settle(e);
            }
        }
    }

    private void settle(ExchangeRequest e) {
        int refundA;
        int refundB;

        switch (e.getStatus()) {
            case COMPLETED -> {
                refundA = Math.round(e.getDepositAmountA() * 0.75f);
                refundB = Math.round(e.getDepositAmountB() * 0.75f);
            }
            case EXPIRED_BOTH -> {
                refundA = Math.round(e.getDepositAmountA() * 0.5f);
                refundB = Math.round(e.getDepositAmountB() * 0.5f);
            }
            case EXPIRED_PARTIAL -> {
                refundA = e.getUserAShowed() ? e.getDepositAmountA() : 0;
                refundB = e.getUserBShowed() ? e.getDepositAmountB() : 0;
            }
            default -> throw new BadRequestException("정산 할 수 없는 상태입니다");
        }
        if (refundA > 0) paymentService.refund(e.getPaymentIdA(), refundA, "교환 정산 환불");
        if (refundB > 0) paymentService.refund(e.getPaymentIdB(), refundB, "교환 정산 환불");

        int feeA = e.getDepositAmountA() - refundA;
        int feeB = e.getDepositAmountB() - refundB;
        SettlementHistoryType type = e.getStatus() == ExchangeRequestStatus.COMPLETED
            ? SettlementHistoryType.EXCHANGE_FEE : SettlementHistoryType.NO_SHOW_FEE;

        Payment paymentA = paymentRepository.findByPaymentId(e.getPaymentIdA())
            .orElseThrow(() -> new NotFoundException("결제 내역을 찾을 수 없습니다."));
        Payment paymentB = paymentRepository.findByPaymentId(e.getPaymentIdB())
            .orElseThrow(() -> new NotFoundException("결제 내역을 찾을 수 없습니다."));
        settlementHistoryService.calculate(e.getShop(), paymentA, feeA, type);
        settlementHistoryService.calculate(e.getShop(), paymentB, feeB, type);

        if (e.getStatus() == ExchangeRequestStatus.EXPIRED_BOTH ||
            e.getStatus() == ExchangeRequestStatus.EXPIRED_PARTIAL) {
            e.getExchangeItemA().getItem().updateStatus(ItemStatus.ON_SALE);
            e.getItemB().updateStatus(ItemStatus.ON_SALE);
        }
        e.getExchangeItemA().updateStatus(ExchangeItemStatus.CLOSED);
    }

    // -------------------------

}
