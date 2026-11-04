package com.goodsbridge.exchange;


import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ErrorCode;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.exchange.dto.ExchangeItemCreateRequest;
import com.goodsbridge.exchange.dto.ExchangeItemResponse;
import com.goodsbridge.exchange.dto.ExchangeRequestCreateRequest;
import com.goodsbridge.exchange.dto.ExchangeRequestResponse;
import com.goodsbridge.item.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeService {

    private final ExchangeRequestRepository exchangeRequestRepository;
    private final ExchangeItemRepository exchangeItemRepository;
//    private final UserRepository userRepository;
//    private final ShopRepository shopRepository;
//    private final ItemRepository itemRepository;

    @Transactional
    public ExchangeItemResponse save(ExchangeItemCreateRequest request, Long userId) {
        //게시글 생성
        //각각의 Repository di해서
        //여기서 user,shop,item Repository로 id 값을 조회해서 값가지고와서 값넣기

//        User user = userRepository.findById(userId)
//            .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND));
//        Shop shop = shopRepository.findById(request.getShopId())
//            .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND));
//        Item item = itemRepository.findById(request.getItemId())
//            .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND));
        //
//        ExchangeItem exchangeItem = ExchangeItem.builder()
//            .user(user)
//            .item(item)
//            .shop(shop)
//            .wantDescription(request.getWantDescription())
//            .build();
//        exchangeItemRepository.save(exchangeItem);
//        return  ExchangeItemResponse.from(exchangeItem)
        return null;
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
        //        Item item = itemRepository.findByUserId(request.getItemId())
//            .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND));
        //1. 로그인한사람이랑 아이템 주인 같은지
        // if(item.id == request.itemBId) {} 확인
        //2. 게시글 주인이랑 같은사람아닌지
        ExchangeItem exchangeItem = exchangeItemRepository.findById(exchangeItemId).orElseThrow(()->{
            throw new  ForbiddenException(ErrorCode.UNAUTHORIZED);
        });
        if (exchangeItem.getUser().getId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.UNAUTHORIZED);
        }
        //3. 게시글 올린 아이템을 교환으로하는게아닌지? 중복
        List<ExchangeRequestStatus> statuses = new ArrayList<>();
        statuses.add(ExchangeRequestStatus.PENDING);
        statuses.add(ExchangeRequestStatus.CONFIRMED);
        if (exchangeRequestRepository.existsByItemBAndStatusIn(request.getItemBId(), statuses)){
            throw new BadRequestException(ErrorCode.INVALID_REQUEST);
        }
        //4. 아직 교환신정 가능한지
        if(!exchangeItem.isOpen()){
            throw new NotFoundException(ErrorCode.NOT_FOUND);
        }
        //5. 포트원 결제하기
        //결제되었느지확인 로직 d
        //6. 결제되면 신청 만들어주기
//        ExchangeRequest exchangeRequest = ExchangeRequest.builder()
//            .exchangeItemA(exchangeItem)
//            .itemB(item)
//            .message(request.getMessage())
//            .shop(exchangeItem.getShop())
//            .depositAmountB(Item.getAmount)
//            .build();
//        return  ExchangeRequestResponse.from(exchangeRequestRepository.save(exchangeRequest));
        return null;
    }



}
