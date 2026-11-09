package com.goodsbridge.item;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CategoryCreateRequest {
    private String name;

    public void validate() {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("카테고리 이름은 필수입니다.");
        }
        if (name.length() > 50) {
            throw new BadRequestException("카테고리 이름은 50자를 초과할 수 없습니다.");
        }
    }
}
