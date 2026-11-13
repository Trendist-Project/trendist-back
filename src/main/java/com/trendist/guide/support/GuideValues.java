package com.trendist.guide.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 가이드 본문의 설정값 자리(4.17): `{{설정이름}}`을 지금 설정값으로 바꾼다. 스킬 수치처럼 관리자가 바꿀 수 있는 숫자를 글에 직접 적지 않아,
 * 설정을 바꿔도 가이드가 어긋나지 않게 하려는 것이다. 사칙 식(`{{poison_ticks*poison_tick_seconds}}`, `{{100-slow_percent}}`, 숫자도 된다, 왼쪽부터 차례로 계산)과
 * 내림(`{{… |floor}}`)을 쓸 수 있다. 숫자는 소수 둘째 자리까지, 끝의 0은 뺀다(3.0 → 3, 0.15 → 0.15).
 * 모르는 이름이 섞이면 그 자리는 그대로 둔다(관리자가 잘못 적어도 글이 깨지지 않게).
 */
public final class GuideValues {
    private GuideValues() {}

    private static final Pattern SLOT = Pattern.compile("\\{\\{\\s*([a-z0-9_.*/+\\- ]+?)\\s*(\\|\\s*floor\\s*)?}}");

    /** value는 설정 이름 → 값. 없는 이름이면 예외를 던져도 되고, 그러면 그 자리는 그대로 남는다 */
    public static String render(String body, ToDoubleFunction<String> value) {
        if (body == null || !body.contains("{{")) return body;
        Matcher m = SLOT.matcher(body);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String text;
            try {
                double v = eval(m.group(1), value);
                if (m.group(2) != null) v = Math.floor(v + 1e-9);
                text = format(v);
            } catch (RuntimeException e) {
                text = m.group();
            }
            m.appendReplacement(out, Matcher.quoteReplacement(text));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** a*b/c, 100-a 처럼 왼쪽부터 차례로 계산한다(곱셈이 먼저인 규칙과 괄호는 없다) */
    private static double eval(String expr, ToDoubleFunction<String> value) {
        Matcher t = Pattern.compile("\\s*([*/+\\-])?\\s*([a-z0-9_.]+)").matcher(expr);
        double acc = 0; boolean first = true; int end = 0;
        while (t.find() && t.start() == end) {
            end = t.end();
            String term = t.group(2);
            double v = Character.isDigit(term.charAt(0)) ? Double.parseDouble(term) : value.applyAsDouble(term);
            if (first) { if (t.group(1) != null) throw new IllegalArgumentException(expr); acc = v; first = false; }
            else if ("*".equals(t.group(1))) acc *= v;
            else if ("/".equals(t.group(1))) acc /= v;
            else if ("+".equals(t.group(1))) acc += v;
            else if ("-".equals(t.group(1))) acc -= v;
            else throw new IllegalArgumentException(expr);
        }
        if (first || end != expr.length()) throw new IllegalArgumentException(expr);
        return acc;
    }

    static String format(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
