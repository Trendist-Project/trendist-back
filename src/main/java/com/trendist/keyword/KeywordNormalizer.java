package com.trendist.keyword;

import com.trendist.common.ApiException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 키워드 정규화(4.18): NFKC → 소문자 → 모든 공백 제거. 같은 단어 판정 키로만 쓰고 화면에는 입력 표기를 쓴다.
 * 등록 조건(4.6): 2~20자, 허용 문자, 개인정보 패턴 금지, 정규화 뒤 재검사.
 */
public final class KeywordNormalizer {
    private static final Pattern ALLOWED = Pattern.compile(
            "^[\\p{IsHangul}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}\\p{IsDevanagari}A-Za-z0-9 ]+$");
    private static final Pattern URL = Pattern.compile("(?i)(https?|www|\\.com|\\.kr|\\.net)");
    private static final Pattern PHONE = Pattern.compile("\\d{9,}");
    private static final Pattern WS = Pattern.compile("\\s+");

    private KeywordNormalizer() {}

    /** 화면 표기: 앞뒤 공백 제거, 연속 공백 하나로. */
    public static String display(String raw) {
        if (raw == null) return "";
        return WS.matcher(Normalizer.normalize(raw, Normalizer.Form.NFC).strip()).replaceAll(" ");
    }

    public static String normalize(String raw) {
        String s = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return WS.matcher(s).replaceAll("");
    }

    /** 검사를 통과하면 정규화 값을 돌려준다. */
    public static String validate(String display) {
        int len = display.codePointCount(0, display.length());
        if (len < 2 || len > 20) throw ApiException.badRequest("KEYWORD_LENGTH", "키워드는 2~20자로 입력해 주세요");
        String nfkc = Normalizer.normalize(display, Normalizer.Form.NFKC);
        if (!ALLOWED.matcher(nfkc).matches()) throw ApiException.badRequest("KEYWORD_CHARS", "쓸 수 없는 문자가 있어요");
        String normalized = normalize(display);
        if (normalized.codePointCount(0, normalized.length()) < 2 || normalized.length() > 100
                || !ALLOWED.matcher(normalized).matches()) {
            throw ApiException.badRequest("KEYWORD_CHARS", "쓸 수 없는 문자가 있어요");
        }
        if (URL.matcher(normalized).find() || PHONE.matcher(normalized).find()) {
            throw ApiException.badRequest("KEYWORD_PRIVATE", "링크나 연락처는 키워드로 쓸 수 없어요");
        }
        return normalized;
    }
}
