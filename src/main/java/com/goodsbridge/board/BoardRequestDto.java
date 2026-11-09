package com.goodsbridge.board;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter // 테스트 편의를 위해 추가하거나
@Builder // 빌더 패턴을 사용합니다
@NoArgsConstructor
@AllArgsConstructor
public class BoardRequestDto {

    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    private Long itemId;
    private Boolean isSecret;
}
