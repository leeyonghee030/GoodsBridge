package com.goodsbridge.exchange;


import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.exchange.dto.*;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.notification.Notification;
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
        if (category == null) {
            return exchangeItemRepository.findAll().stream()
                .map(ExchangeItemResponse::from).toList();
        } else {
            return exchangeItemRepository.findByItem_Category_Id(category).stream()
                .map(ExchangeItemResponse::from).toList();
        }
    }

    //내 게시글 목록
    public List<ExchangeItemResponse> getMyExchangeItems(Long userId) {
        return exchangeItemRepository.findByUser_Id(userId).stream()
            .map(ExchangeItemResponse::from).toList();
    }

    //교환 신청
    @Transactional
    public ExchangeRequestResponse proposeExchange(Long exchangeItemId, ExchangeRequestCreateRequest request, Long userId) {
        Item item = itemRepository.findById(request.getItemBId())
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
        //3. 게시글 올린 아이템을 교환으로하는게아닌지? 중복
        List<ExchangeRequestStatus> statuses = new ArrayList<>();
        statuses.add(ExchangeRequestStatus.PENDING);
        statuses.add(ExchangeRequestStatus.CONFIRMED);
        if (exchangeRequestRepository.existsByItemBAndStatusIn(request.getItemBId(), statuses)) {
            throw new BadRequestException("이미 다른 제안에 쓰이고 있는 아이템입니다.");
        }
        //4. 아직 교환신정 가능한지
        if (!exchangeItem.isOpen()) {
            throw new BadRequestException("이미 매칭되었거나 마감된 게시글입니다.");
        }
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
        // a 한테 교환신청한 다른 b 들 환불
        List<ExchangeRequest> exchangeRequestList = exchangeRequestRepository.findActiveRequestsByItem(exchangeRequest.getExchangeItemA());
        for (ExchangeRequest e : exchangeRequestList) {
            paymentService.refund(e.getPaymentIdB(), e.getDepositAmountB(), "교환 취소");
            //상태변경
            e.updateStatus(ExchangeRequestStatus.CANCELLED);
        }
        exchangeRequest.updateDeadlineForVisit();

        // TODO - 교환 신청 메시지 생성
        notificationService.createNotification(
            userId,
            Notification.Type.EXCHANGE_APPROVED,
            "교환 신청 승인",
            "신청한 교환이 승인되었습니다.",
            Notification.ReferenceType.EXCHANGE,
            exchangeRequest.getId()
        );

        return ExchangeRequestResponse.from(exchangeRequest);
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
        if (exchangeRequest.isBothShowed()) {
            exchangeRequest.updateStatus(ExchangeRequestStatus.COMPLETED);
            settle(exchangeRequest);
        }
        return ExchangeRequestResponse.from(exchangeRequest);
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
