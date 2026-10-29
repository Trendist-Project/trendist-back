package com.trendist.common.support;

import com.trendist.common.exception.ApiException;
import java.text.Normalizer;
import java.util.regex.Pattern;

/** 사용자가 쓰는 글(댓글 4.12, 채팅 4.13)의 공통 검사: 정리, 길이, 링크·전화번호 차단. */
public final class TextRules {
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\\n]]");
    private static final Pattern LINK = Pattern.compile("(?i)(https?://|www\\.|[a-z0-9-]+\\.(com|net|org|kr|io|me|ly|gg|co)\\b)");
    private static final Pattern PHONE = Pattern.compile("\\d{2,4}[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}");

    private TextRules() {}

    /** NFC로 맞추고 제어·보이지 않는 문자를 지운 뒤 앞뒤 공백을 자른다. 줄바꿈은 allowNewline일 때만 남긴다(연속 3줄 이상은 2줄로). */
    public static String clean(String raw, boolean allowNewline) {
        String s = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n');
        s = CONTROL.matcher(s).replaceAll("");
        s = allowNewline ? s.replaceAll("\n{3,}", "\n\n") : s.replace('\n', ' ');
        return s.strip();
    }

    /** 화면에 보이는 글자 수(코드 포인트). */
    public static int length(String s) { return s.codePointCount(0, s.length()); }

    /** 길이와 링크·전화번호를 검사한다. 통과하지 못하면 prefix_LENGTH / prefix_LINK / prefix_PHONE. */
    public static void check(String s, int min, int max, String prefix, String what) {
        int n = length(s);
        if (n < min || n > max) throw ApiException.badRequest(prefix + "_LENGTH", what + "은(는) " + min + "~" + max + "자로 써 주세요");
        if (LINK.matcher(s).find()) throw ApiException.badRequest(prefix + "_LINK", "링크는 쓸 수 없어요");
        if (PHONE.matcher(s).find()) throw ApiException.badRequest(prefix + "_PHONE", "전화번호는 쓸 수 없어요");
    }
}
