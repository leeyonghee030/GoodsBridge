package com.goodsbridge.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatConfigTest {

    @Test
    void systemPrompt_includesTheServiceGuideAndTheDontKnowRule() {
        String prompt = ChatConfig.SYSTEM_PROMPT;

        // 서비스 안내문이 프롬프트에 들어가 있고, 모르는 내용은 지어내지 않게 하는 규칙이 있다
        assertThat(prompt).contains("GoodsBridge 서비스 안내").contains("최소 2,000원").contains("(/exchange)");
        assertThat(prompt).contains("지어내지 마세요").contains("[상품](/test/item)");
    }
}
