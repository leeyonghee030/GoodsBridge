package com.goodsbridge.item;

import com.goodsbridge._core.exception.BadRequestException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class ItemCreateRequest {
    @NotNull(message = "가게 ID는 필수 입력 값입니다.") // 💡 [추가] 상품 등록 시 가게 선택 강제화
    private Long shopId;

    @NotNull(message = "카테고리 ID는 필수 입력 값입니다.")
    private Long categoryId;

    @NotBlank(message = "상품 제목은 필수 입력 값입니다.")
    private String title;

    private String description;

    @NotNull(message = "상품 가격은 필수 입력 값입니다.")
    @Min(value = 1, message = "가격은 0보다 커야 합니다.")
    private Integer price;

    @NotNull(message = "판매 타입은 필수 입력 값입니다.")
    private SaleType saleType;

    private String imageUrl;

    public void validate() {
        if (shopId == null || shopId <= 0) {
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
    }
}
