package com.goodsbridge.exchange.dto;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ExchangeCommentRequest {
    private String content;

    public void validate() {
        if (this.content == null || this.content.trim().isEmpty()){
        throw new BadRequestException("댓글작성은 필수입니다");}
        if (this.content.length() > 255) {
            throw new BadRequestException("댓글은 255자 이하로 작성해주세요.");
        }
    }
}
