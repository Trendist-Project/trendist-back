package com.trendist.player.support;

import com.trendist.common.exception.ApiException;
import java.text.Normalizer;
import java.util.regex.Pattern;

/** 닉네임 규칙(4.1): 1~10자, 영문·숫자·공백·_ - . 와 한 가지 문자권, 첫 글자 - 금지. */
public final class NicknameValidator {
    private static final Pattern BASIC = Pattern.compile("[A-Za-z0-9 _.\\-]");
    private static final Pattern WS = Pattern.compile("\\s+");

    private NicknameValidator() {}

    public static String clean(String raw) {
        if (raw == null) raw = "";
        StringBuilder sb = new StringBuilder();
        // 전각 영숫자는 반각으로
        for (int cp : Normalizer.normalize(raw, Normalizer.Form.NFC).codePoints().toArray()) {
            if (cp >= 0xFF01 && cp <= 0xFF5E) cp -= 0xFEE0;
            else if (cp == 0x3000) cp = ' ';
            sb.appendCodePoint(cp);
        }
        String s = WS.matcher(sb.toString().strip()).replaceAll(" ");
        int len = s.codePointCount(0, s.length());
        if (len < 1 || len > 10) throw ApiException.badRequest("NICKNAME_LENGTH", "닉네임은 1~10자로 정해 주세요");
        if (s.startsWith("-")) throw ApiException.badRequest("NICKNAME_CHARS", "닉네임에 쓸 수 없는 문자가 있어요: -");
        String script = null;
        for (int cp : s.codePoints().toArray()) {
            String ch = new String(Character.toChars(cp));
            if (BASIC.matcher(ch).matches()) continue;
            String sc = scriptOf(cp);
            if (sc == null) throw ApiException.badRequest("NICKNAME_CHARS", "닉네임에 쓸 수 없는 문자가 있어요: " + ch);
            if (script != null && !script.equals(sc)) {
                // 일본어는 가나와 한자를 섞어 쓴다
                boolean japanese = (script.equals("JAPANESE") || script.equals("HAN")) && (sc.equals("JAPANESE") || sc.equals("HAN"));
                if (!japanese) throw ApiException.badRequest("NICKNAME_MIXED", "닉네임에는 한 가지 문자만 섞어 쓸 수 있어요");
                sc = "JAPANESE";
            }
            script = sc;
        }
        return s;
    }

    private static String scriptOf(int cp) {
        Character.UnicodeScript us = Character.UnicodeScript.of(cp);
        // 한글은 완성형 음절만(자모 단독 금지)
        if (us == Character.UnicodeScript.HANGUL) return (cp >= 0xAC00 && cp <= 0xD7A3) ? "HANGUL" : null;
        if (us == Character.UnicodeScript.HIRAGANA || us == Character.UnicodeScript.KATAKANA) return "JAPANESE";
        if (us == Character.UnicodeScript.HAN) return "HAN";
        if (us == Character.UnicodeScript.DEVANAGARI) return "DEVANAGARI";
        return null;
    }
}
