package com.goodsbridge.exchange;


import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.exchange.dto.*;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.shop.ShopStatus;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.Status;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

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
    private final EntityManager entityManager;
    private final ExchangeCommentRepository exchangeCommentRepository;

    //교환 댓글 service--------------
    @Transactional
    public ExchangeCommentResponse saveComment(Long id, Long userId, ExchangeCommentRequest request) {
    //조회하기
         ExchangeItem exchangeItem = exchangeItemRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 교환 게시글 입니다."));
         User user = userRepository.findById(userId)
             .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자 입니다."));
         if (user.getStatus() != Status.ACTIVE){
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
            .orElseThrow(()->{
                throw new NotFoundException("존재하지 않는 댓글입니다");});
         if (!exchangeComment.getUser().getId().equals(userId)){
             throw new ForbiddenException("작성자만 수정 할수있습니다");
         }
        if (exchangeComment.getUser().getStatus() != Status.ACTIVE){
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
            .orElseThrow(()->{
                throw new NotFoundException("존재하지 않는 댓글입니다");});

        if (user.getStatus() != Status.ACTIVE){
            throw new ForbiddenException("활성화된 유저만 댓글 삭제 가능합니다");
        }

        if (!exchangeComment.getUser().getId().equals(userId) && user.getRole() != Role.ADMIN){
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
        if (exchangeRequestRepository.existsByItemBAndStatusIn(request.getItemBId(), statuses)){
            throw new BadRequestException("이미 다른 제안에 쓰이고 있는 아이템입니다.");
        }
        //4. 아직 교환신정 가능한지
        if(!exchangeItem.isOpen()){
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
            .orElseThrow(()-> new NotFoundException("존재하지 않는 교환 제안입니다"));
        //유저가 교환신청한사람맞는지?
        if (!exchangeRequest.isProposer(userId)){
            throw new ForbiddenException("교환 신청자만 교환 취소 할 수있습니다");
        }
        //상태가 대기중 맞는지
        if (!exchangeRequest.isPending()){
            throw new BadRequestException("대기중인 제안만 취소할 수 있습니다");
        }
        // 보증금환불
        paymentService.refund(exchangeRequest.getPaymentIdB(),exchangeRequest.getDepositAmountB(),"교환 취소");
        //상태변경
        exchangeRequest.updateStatus(ExchangeRequestStatus.CANCELLED);
    }

    public List<ExchangeRequestResponse> getMyExchange(Long userId) {
        List<ExchangeRequest> exchangeRequestList = exchangeRequestRepository.findByExchangeItemA_User_IdOrItemB_SellerUserId(userId);
        return exchangeRequestList.stream()
            .map(ExchangeRequestResponse::from).toList();
    }



    // -------------------------
}
