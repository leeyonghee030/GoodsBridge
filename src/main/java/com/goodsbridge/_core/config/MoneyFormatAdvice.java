package com.goodsbridge._core.config;

import com.samskivert.mustache.Mustache;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Mustache 화면에서 금액을 천 단위 콤마로 보여주기 위한 공통 람다.
 * 사용: {{#money}}{{price}}{{/money}}원 → 15,000원
 */
@ControllerAdvice
public class MoneyFormatAdvice {

    @ModelAttribute("money")
    public Mustache.Lambda money() {
        return (fragment, out) -> {
            String raw = fragment.execute().trim();
            try {
                out.write(NumberFormat.getNumberInstance(Locale.KOREA).format(Long.parseLong(raw)));
            } catch (NumberFormatException e) {
                out.write(raw);
            }
        };
    }
}
