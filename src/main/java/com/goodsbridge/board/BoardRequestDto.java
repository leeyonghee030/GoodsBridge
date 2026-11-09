package com.goodsbridge.board;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BoardRequestDto {
    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    private Long itemId;      // 상품 연관 ID (선택)
    private Boolean isSecret; // 비밀글 여부 (기본값 false 처리)
}
