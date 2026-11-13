package com.goodsbridge.item;

import com.goodsbridge._core.exception.BadRequestException;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class ItemUpdateRequest {
    // 가게 없는 상품(교환 화면에서 등록)은 가게 ID 없이 수정 — 가게 상품의 필수 여부는 ItemService에서 검증
    private Long shopId;
    @NotNull(message = "카테고리 ID는 필수 입력 값입니다.")
    private Long categoryId;
    private String title;
    private String description;
    private Integer price;
    private SaleType saleType;
    private ItemStatus status;
    private String imageUrl;

    public void validate() {
        if (shopId != null && shopId <= 0) {
            throw new BadRequestException("유효한 가게 ID를 입력해주세요.");
        }
        if (categoryId == null || categoryId <= 0) {
            throw new BadRequestException("유효한 카테고리 ID를 입력해주세요.");
        }
        if (title == null || title.isBlank()) {
            throw new BadRequestException("상품 제목은 필수입니다.");
        }
        if (title.length() > 100) {
            throw new BadRequestException("상품 제목은 100자 이내여야 합니다.");
        }
        if (description != null && description.length() > 5000) {
            throw new BadRequestException("상품 설명은 5000자 이내여야 합니다.");
        }
        if (price == null || price <= 0) {
            throw new BadRequestException("가격은 0보다 커야 합니다.");
        }
        if (price > 100_000_000) {
            throw new BadRequestException("상품 가격은 1억 원을 초과할 수 없습니다.");
        }
        if (saleType == null) {
            throw new BadRequestException("판매 타입은 필수입니다.");
        }
        if (status == null) {
            throw new BadRequestException("상품 상태는 필수입니다.");
        }
    }
}
