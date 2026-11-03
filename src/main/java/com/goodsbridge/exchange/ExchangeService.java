package com.goodsbridge.exchange;


import com.goodsbridge.exchange.dto.ExchangeItemCreateRequest;
import com.goodsbridge.exchange.dto.ExchangeItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
