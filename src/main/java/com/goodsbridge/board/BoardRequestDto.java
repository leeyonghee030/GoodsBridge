package com.goodsbridge.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardRequestDto {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 100, message = "제목은 100자 이내로 입력해주세요.")
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 5000, message = "내용은 5000자 이내로 입력해주세요.")
    private String content;

    private Long itemId;

    @NotNull(message = "게시판 유형은 필수입니다.")
    private BoardType boardType;

    private Boolean isSecret;

    // 상품 문의: 문의 대상. 비우면 상품(itemId)에 대한 문의다. 이벤트/경매는 targetId 를 함께 보낸다.
    private InquiryTargetType targetType;
    private Long targetId;

    // 상품 리뷰: 리뷰를 쓰는 구매. 비우면 itemId 로 리뷰를 쓸 수 있는 내 구매를 서버가 고른다.
    private Long purchaseId;
}
