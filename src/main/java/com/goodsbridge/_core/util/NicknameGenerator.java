package com.goodsbridge._core.util;

import java.util.Random;

public class NicknameGenerator {
    private static final String[] ADJECTIVES = {
        "조용한", "행복한", "느긋한", "따뜻한",
        "신나는", "성실한", "귀여운", "부지런한",
        "자유로운", "반짝이는", "용감한", "평화로운"
    };

    private static final String[] NOUNS = {
        "고래", "토끼", "상점", "나무", "여우",
        "구름", "펭귄", "감자", "다람쥐", "사장님"
    };

    private static final Random random = new Random();

    public static String generate() {

        String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[random.nextInt(NOUNS.length)];
        int number = random.nextInt(9000) + 1000;

        return adjective + noun + number;
    }
}
