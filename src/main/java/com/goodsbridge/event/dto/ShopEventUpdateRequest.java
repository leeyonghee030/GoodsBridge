package com.goodsbridge.event.dto;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.event.ShopEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 이벤트 수정 요청. 보낸 값만 바꾼다 (안 보낸 값은 그대로).
//  - 설명, 방문 날짜: 이벤트가 끝나기 전이면 언제든 수정 가능
//  - 수량, 참가비, 1인 최대 수량, 상품 가격, 모집 마감: 참가자가 아직 없고 모집 마감 전일 때만 수정 가능
@Getter
@NoArgsConstructor
public class ShopEventUpdateRequest {
    private String itemDescription;
    private LocalDate visitUntil;

    private Integer capacity;
    private Integer feeAmount;
    private Integer maxPerUser;
    private Integer itemPrice;
    private LocalDateTime deadline;

    // 참가자가 있으면 못 바꾸는 항목을 바꾸려는지 (현재 값과 같으면 바꾸는 게 아니다)
    public boolean changesLockedFields(ShopEvent current) {
        return (capacity != null && !capacity.equals(current.getCapacity()))
            || (feeAmount != null && !feeAmount.equals(current.getFeeAmount()))
            || (maxPerUser != null && !maxPerUser.equals(current.getMaxPerUser()))
            || (itemPrice != null && !itemPrice.equals(current.getItem().getPrice()))
            || (deadline != null && !deadline.equals(current.getDeadline()));
    }

    // 바꾼 뒤의 값이 이벤트 열기와 같은 규칙을 지키는지
    public void validate(ShopEvent current) {
        int newCapacity = capacity != null ? capacity : current.getCapacity();
        int newFee = feeAmount != null ? feeAmount : current.getFeeAmount();
        int newMax = maxPerUser != null ? maxPerUser : current.getMaxPerUser();
        int newPrice = itemPrice != null ? itemPrice : current.getItem().getPrice();
        LocalDateTime newDeadline = deadline != null ? deadline : current.getDeadline();
        LocalDate newVisit = visitUntil != null ? visitUntil : current.getVisitUntil();

        if (newCapacity <= 0 || newCapacity > 100) {
            throw new BadRequestException("모집 수량은 1-100개 사이 값이여야 합니다.");
        }
        if (newFee < 5000 || newFee > 100000) {
            throw new BadRequestException("참가비는 5,000 - 100,000 사이 값이여야  합니다.");
        }
        if (newMax < 1 || newMax > EventParticipationRequest.MAX_QUANTITY || newMax > newCapacity) {
            throw new BadRequestException("1인당 최대 구매 수량은 1-" + EventParticipationRequest.MAX_QUANTITY + "개, 모집 수량 이하로 입력해 주세요.");
        }
        if (newPrice <= newFee) {
            throw new BadRequestException("상품 가격은 참가비보다 커야 합니다.");
        }
        if (deadline != null && deadline.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("마감 시각은 현재보다 미래여야 합니다.");
        }
        if (newVisit.isBefore(newDeadline.toLocalDate())) {
            throw new BadRequestException("방문 날짜는 모집 마감일 이후여야 합니다.");
        }
        if (visitUntil != null && visitUntil.isBefore(LocalDate.now())) {
            throw new BadRequestException("방문 날짜는 오늘 이후여야 합니다.");
        }
        if (itemDescription != null && itemDescription.length() > 1000) {
            throw new BadRequestException("상품 설명은 1000자 이하로 입력해 주세요.");
        }
    }
}
