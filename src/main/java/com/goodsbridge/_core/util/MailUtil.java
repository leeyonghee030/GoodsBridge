package com.goodsbridge._core.util;

import java.security.SecureRandom;

public class MailUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
