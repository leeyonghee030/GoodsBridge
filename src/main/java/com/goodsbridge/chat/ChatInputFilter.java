package com.goodsbridge.chat;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * AI를 부르기 전에 서버에서 질문을 걸러 내는 정규식 필터.
 *
 * <p>[왜 필요한가] AI 호출은 무료 한도(토큰)를 쓰고, 프롬프트 주입("이전 지시 무시")의 통로가 된다.
 * 서비스와 관계없거나 위험한 질문은 AI에게 보내지 않고 서버가 정해 둔 문장으로 바로 답한다.
 *
 * <p>[종류]
 * <ul>
 *   <li>INJECTION: 시스템 프롬프트 노출, 지시 무시, 역할 바꾸기 요청. 서비스 단어가 섞여 있어도 항상 막는다.</li>
 *   <li>OFF_TOPIC: 코딩, 날씨, 번역, 글쓰기, 주식 같은 서비스 밖 요청. 서비스 단어(교환·경매 등)가 섞여 있어도 항상 막는다.
 *       대신 서비스 질문과 겹칠 수 있는 말은 단어 목록에 넣지 않는다 (예: "체인점 코드 알려줘"는 통과).</li>
 * </ul>
 * 정규식은 완벽한 방어가 아니다. 걸러지지 않은 질문은 시스템 프롬프트의 규칙이 한 번 더 막는다.
 */
final class ChatInputFilter {

    enum Verdict { ALLOWED, INJECTION, OFF_TOPIC }

    /** 막았을 때 사용자에게 돌려주는 정해진 문장 (시스템 프롬프트의 거절 문장과 같다). */
    static final String REFUSAL_MESSAGE = "저는 GoodsBridge 도우미라서 우리 서비스에 대한 질문만 답할 수 있어요. "
        + "날씨, 번역, 글쓰기, 주식, 코딩 같은 질문은 도와드릴 수 없어요. "
        + "교환, 보증금, 경매, 이벤트, 내 교환 신청처럼 GoodsBridge에 대해 물어봐 주세요.";

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    // 띄어쓰기를 없앤 문장에서 찾는다 ("시 스 템 프 롬 프 트" 같은 우회 방지)
    private static final List<Pattern> INJECTION = compile(
        "시스템프롬프트", "systemprompt", "프롬프트(를|좀)?(알려|보여|공개|출력|말해|복사)", "프롬프트가뭐",
        "(이전|위|앞|기존|모든)(의)?(지시|명령|규칙|지침|설정|프롬프트)(를|을|은|는)?(다)?(무시|잊어|취소|버려)",
        "(지시|명령|규칙|지침|제한)(을|를)?(모두|다)?(무시|해제|풀어)",
        "ignore(all)?(the)?(previous|prior|above|earlier|your)", "disregard(all)?(the)?(previous|prior|above)",
        "forget(all)?(the)?(previous|prior|your)(instructions|rules)", "jailbreak", "탈옥", "dan모드", "개발자모드",
        "developermode", "(너의?|네)(규칙|설정|지침|역할|프롬프트)(을|를)?(알려|말해|보여)", "지금부터(너는|넌|당신은)",
        "youarenow", "actas", "롤플레이", "역할극", "시스템메시지", "systemmessage", "시스템지시"
    );

    // 코딩 요청: 서비스 단어가 섞여 있어도 항상 막는다 ("교환 코드 짜줘"처럼 서비스 단어로 우회하는 것을 막기 위해).
    // 서비스 질문에 쓰일 수 있는 말은 넣지 않는다 ("체인점 코드 알려줘"의 '코드 알려'는 넣지 않는다).
    private static final List<Pattern> CODING = compile(
        "코딩", "프로그래밍", "프로그램(을|좀)?(짜|만들|작성)", "코드(를|좀)?(짜|작성|만들|써|리뷰)", "소스코드",
        "파이썬", "python", "자바스크립트", "javascript", "typescript", "타입스크립트", "java(?![가-힣a-z])", "자바(로|를|코드)",
        "c\\+\\+", "kotlin", "코틀린", "html", "css", "sql", "쿼리(를|좀)?(짜|작성|만들)", "알고리즘", "함수(를|좀)?(짜|만들|작성)",
        "버그(를|좀)?(고쳐|수정|찾아)", "리액트", "깃허브", "github", "정규식(을|를)?(짜|만들)", "```"
    );

    // 그 밖에 사람들이 AI에게 많이 묻는 서비스 밖 요청(날씨, 번역, 글쓰기, 주식 등): 서비스 단어가 섞여 있어도 항상 막는다.
    // 서비스 질문과 겹치는 말은 넣지 않는다 ('사주'는 "사주세요", '타로'·'소설'은 굿즈 이름과 겹친다).
    private static final List<Pattern> OFF_TOPIC = compile(
        // 글쓰기·번역
        "시(를|좀)?한편", "(?<![가-힣])시(를|좀)?(써줘|써봐|지어줘|지어봐|작성해줘)", "소설(을|좀)?(써|지어|작성)", "작사(해|를|좀)", "작곡(해|를|좀)", "에세이(를|좀)?(써|작성)", "자기소개서", "번역(해|좀|기|부탁)", "영어로", "영작", "맞춤법", "스프링", "요약(해|좀)?줘.{0,20}(글|기사|논문)",
        "숙제", "과제(를|좀)?(해|도와)", "수학문제(풀|좀|알려|해)", "논문(써|작성|요약|추천|찾아)", "독후감",
        // 일반 상식·실시간 정보
        "날씨", "기온", "미세먼지", "주식(시세|투자|추천|매수|매도|종목|차트|전망|사도|살까)", "코인(시세|투자|추천|거래소|전망)", "비트코인", "이더리움", "환율", "로또", "운세", "사주팔자", "레시피", "요리법", "맛집",
        "뉴스(좀|알려|기사|속보|요약)", "정치(얘기|이야기|토론|성향|뉴스)", "대통령(누구|이름|선거)", "선거(결과|투표|후보)", "연애상담", "다이어트(방법|식단)", "병원(추천|어디|예약|가까운)", "처방(해|좀|전)", "증상(이|좀|알려)", "소송", "법률상담", "여행지추천", "영화추천", "노래추천",
        "농담(해|좀)", "재밌는이야기", "끝말잇기", "수수께끼"
    );

    private ChatInputFilter() {
    }

    static Verdict check(String message) {
        String squashed = squash(message);
        if (matchesAny(INJECTION, squashed)) {
            return Verdict.INJECTION;
        }
        // 서비스 단어가 섞여 있어도 막는다 ("교환 날씨 알려줘", "경매 코드 짜줘"로 필터를 우회하지 못하게)
        if (matchesAny(CODING, squashed) || matchesAny(OFF_TOPIC, squashed)) {
            return Verdict.OFF_TOPIC;
        }
        return Verdict.ALLOWED;
    }

    // 전각·호환 문자를 보통 글자로 맞추고(NFKC), 보이지 않는 글자와 공백·기호를 모두 없앤다
    private static String squash(String message) {
        String normalized = Normalizer.normalize(message, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[\\p{Cf}\\s.,!?~\\-_*'\"·ㆍ]+", "");
    }

    private static boolean matchesAny(List<Pattern> patterns, String text) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<Pattern> compile(String... regexes) {
        return java.util.Arrays.stream(regexes).map(r -> Pattern.compile(r, FLAGS)).toList();
    }
}
