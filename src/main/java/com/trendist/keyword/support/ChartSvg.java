package com.trendist.keyword.support;

import com.trendist.keyword.dto.KeywordDtos;
import com.trendist.share.support.SharePage;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 키워드 활동 그래프를 SVG 문자열로 그린다(4.28). 게임 안 키워드 팝업의 `ActivityChart.tsx`와 같은 모양이다:
 * 검색 수(실선)·추가 수(파선) 곡선과 그라데이션 면, 가장 높은 점의 "최고", 집계 중인 마지막 구간(점선·깜빡이는 점),
 * 켜면 보이는 가진 사람 수(오른쪽 축, 점선). 서버에서 한 번 그리므로 마우스를 올리면 뜨는 설명 상자만 없다.
 * 숫자와 고정 글자만 쓰므로 사용자 입력이 들어가지 않는다.
 */
final class ChartSvg {
    private static final int W = 560, H = 200, TOP = 18, RIGHT = 40, BOTTOM = 26, LEFT = 34;
    private static final String SEARCH = "#5aa9e6", PLUS = "#3fb68b", HOLDERS = "#f2b84b";

    private ChartSvg() {}

    /** 한 기간의 그래프 블록(그림 + 범례 + 요약 문장). range: 24h / 7d / 30d / 180d */
    static String block(KeywordDtos.Activity a, boolean en) {
        List<KeywordDtos.ActivityPoint> pts = a.points();
        String range = a.range();
        boolean hourly = "24h".equals(range);
        boolean empty = pts.stream().allMatch(p -> p.search() == 0 && p.plus() == 0);
        StringBuilder b = new StringBuilder("<div class=\"rng rng-").append(range).append("\">");
        if (empty) b.append("<p class=\"chart-empty muted\">").append(en ? "No activity yet" : "아직 기록이 없어요").append("</p>");
        else b.append(svg(a, pts, en, hourly));
        b.append("<div class=\"chart-legend\" aria-hidden=\"true\">")
                .append("<span><i class=\"solid\" style=\"border-color:").append(SEARCH).append("\"></i>").append(en ? "Searches" : "검색").append("</span>")
                .append("<span><i class=\"dash\" style=\"border-color:").append(PLUS).append("\"></i>").append(en ? "Adds" : "추가").append("</span>")
                .append("<span class=\"h-el\"><i class=\"dot\" style=\"border-color:").append(HOLDERS).append("\"></i>").append(en ? "Holders" : "가진 사람").append("</span>")
                .append("</div>");
        b.append("<p class=\"chart-summary\">").append(SharePage.escape(summary(a, en))).append("</p></div>");
        return b.toString();
    }

    private static String svg(KeywordDtos.Activity a, List<KeywordDtos.ActivityPoint> pts, boolean en, boolean hourly) {
        String range = a.range();
        int n = pts.size();
        double innerW = W - LEFT - RIGHT, innerH = H - TOP - BOTTOM;
        double yMax = niceMax(Math.max(1, pts.stream().mapToLong(p -> Math.max(p.search(), p.plus())).max().orElse(1)));
        double hMax = niceMax(Math.max(1, pts.stream().mapToLong(KeywordDtos.ActivityPoint::holders).max().orElse(1)));
        int labelEvery = switch (range) { case "24h" -> 6; case "7d" -> 1; case "30d" -> 5; default -> 30; };
        boolean liveLast = pts.get(n - 1).live();
        int lastDone = n - (liveLast ? 2 : 1);
        int peak = 0;
        for (int i = 1; i < n; i++) if (pts.get(i).search() + pts.get(i).plus() > pts.get(peak).search() + pts.get(peak).plus()) peak = i;
        Fmt f = new Fmt(a.timezone(), range, en);

        StringBuilder b = new StringBuilder();
        b.append("<svg viewBox=\"0 0 ").append(W).append(' ').append(H).append("\" role=\"img\" aria-label=\"")
                .append(SharePage.escape(summary(a, en))).append("\" class=\"chart-svg\">");
        b.append("<defs>")
                .append(grad("search-" + range, SEARCH, 0.28)).append(grad("plus-" + range, PLUS, 0.22))
                .append("<clipPath id=\"reveal-").append(range).append("\"><rect class=\"chart-reveal\" x=\"0\" y=\"0\" width=\"").append(W)
                .append("\" height=\"").append(H).append("\"/></clipPath></defs>");
        for (double fr : new double[]{0, 0.5, 1}) {
            double gy = TOP + innerH * (1 - fr);
            b.append("<line x1=\"").append(LEFT).append("\" x2=\"").append(W - RIGHT).append("\" y1=\"").append(num(gy)).append("\" y2=\"").append(num(gy)).append("\" class=\"chart-grid\"/>");
            b.append("<text x=\"").append(LEFT - 6).append("\" y=\"").append(num(gy + 4)).append("\" class=\"chart-axis\" text-anchor=\"end\">").append(Math.round(yMax * fr)).append("</text>");
            b.append("<text x=\"").append(W - RIGHT + 6).append("\" y=\"").append(num(gy + 4)).append("\" class=\"chart-axis holders h-el\">").append(Math.round(hMax * fr)).append("</text>");
        }
        for (int i = 0; i < n; i++) {
            if (i % labelEvery != 0 && i != n - 1) continue;
            KeywordDtos.ActivityPoint p = pts.get(i);
            String label = p.live() ? (hourly ? (en ? "Now" : "지금") : (en ? "Today" : "오늘")) : f.tick(p);
            String anchor = i == 0 ? "start" : i == n - 1 ? "end" : "middle";
            b.append("<text x=\"").append(num(x(i, n, innerW))).append("\" y=\"").append(H - 6).append("\" class=\"chart-axis\" text-anchor=\"").append(anchor).append("\">")
                    .append(SharePage.escape(label)).append("</text>");
        }

        double base = TOP + innerH;
        List<double[]> sDone = new ArrayList<>(), pDone = new ArrayList<>(), hAll = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            KeywordDtos.ActivityPoint p = pts.get(i);
            if (i <= lastDone) {
                sDone.add(new double[]{x(i, n, innerW), y(p.search(), yMax, innerH)});
                pDone.add(new double[]{x(i, n, innerW), y(p.plus(), yMax, innerH)});
            }
            hAll.add(new double[]{x(i, n, innerW), y(p.holders(), hMax, innerH)});
        }
        b.append("<g clip-path=\"url(#reveal-").append(range).append(")\">");
        b.append(area(sDone, base, "url(#grad-search-" + range + ")")).append(area(pDone, base, "url(#grad-plus-" + range + ")"));
        b.append("<path d=\"").append(smoothPath(sDone)).append("\" class=\"chart-line\" stroke=\"").append(SEARCH).append("\"/>");
        b.append("<path d=\"").append(smoothPath(pDone)).append("\" class=\"chart-line dashed\" stroke=\"").append(PLUS).append("\"/>");
        if (liveLast && !sDone.isEmpty()) {
            KeywordDtos.ActivityPoint last = pts.get(n - 1);
            double lx = x(n - 1, n, innerW);
            double[] s0 = sDone.get(sDone.size() - 1), p0 = pDone.get(pDone.size() - 1);
            b.append("<path d=\"M").append(num(s0[0])).append(',').append(num(s0[1])).append(" L").append(num(lx)).append(',').append(num(y(last.search(), yMax, innerH)))
                    .append("\" class=\"chart-line live\" stroke=\"").append(SEARCH).append("\"/>");
            b.append("<path d=\"M").append(num(p0[0])).append(',').append(num(p0[1])).append(" L").append(num(lx)).append(',').append(num(y(last.plus(), yMax, innerH)))
                    .append("\" class=\"chart-line live\" stroke=\"").append(PLUS).append("\"/>");
        }
        b.append("<path d=\"").append(smoothPath(hAll)).append("\" class=\"chart-line holders h-el\" stroke=\"").append(HOLDERS).append("\"/>");
        b.append("</g>");

        KeywordDtos.ActivityPoint pk = pts.get(peak);
        double peakY = y(Math.max(pk.search(), pk.plus()), yMax, innerH);
        b.append("<g class=\"chart-peak\"><circle cx=\"").append(num(x(peak, n, innerW))).append("\" cy=\"").append(num(peakY)).append("\" r=\"4\"/>")
                .append("<text x=\"").append(num(x(peak, n, innerW))).append("\" y=\"").append(num(peakY - 9)).append("\" text-anchor=\"middle\">")
                .append(en ? "Peak" : "최고").append("</text></g>");
        if (liveLast) {
            KeywordDtos.ActivityPoint last = pts.get(n - 1);
            b.append("<g class=\"chart-live-dot\"><circle cx=\"").append(num(x(n - 1, n, innerW))).append("\" cy=\"").append(num(y(last.search(), yMax, innerH)))
                    .append("\" r=\"3.5\" fill=\"").append(SEARCH).append("\"/><circle cx=\"").append(num(x(n - 1, n, innerW))).append("\" cy=\"")
                    .append(num(y(last.plus(), yMax, innerH))).append("\" r=\"3.5\" fill=\"").append(PLUS).append("\"/></g>");
        }
        return b.append("</svg>").toString();
    }

    private static String grad(String id, String color, double opacity) {
        return "<linearGradient id=\"grad-" + id + "\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\"><stop offset=\"0%\" stop-color=\"" + color + "\" stop-opacity=\"" + opacity
                + "\"/><stop offset=\"100%\" stop-color=\"" + color + "\" stop-opacity=\"0\"/></linearGradient>";
    }

    private static String area(List<double[]> pts, double base, String fill) {
        if (pts.isEmpty()) return "";
        double[] first = pts.get(0), last = pts.get(pts.size() - 1);
        return "<path d=\"" + smoothPath(pts) + " L" + num(last[0]) + "," + num(base) + " L" + num(first[0]) + "," + num(base) + " Z\" fill=\"" + fill + "\"/>";
    }

    private static double x(int i, int n, double innerW) { return LEFT + (n == 1 ? innerW / 2 : (double) i / (n - 1) * innerW); }
    private static double y(long v, double max, double innerH) { return TOP + innerH - (v / max) * innerH; }

    /** 축 최댓값을 보기 좋은 짝수 계열(2, 4, 6, 10 × 10ⁿ)로 올린다(chartMath.ts niceMax와 같다). */
    static double niceMax(double v) {
        double exp = Math.pow(10, Math.floor(Math.log10(v)));
        double n = v / exp;
        if (exp >= 10 && n <= 1) return exp;
        return (n <= 2 ? 2 : n <= 4 ? 4 : n <= 6 ? 6 : 10) * exp;
    }

    /** 단조 3차 보간(Fritsch–Carlson, chartMath.ts smoothPath와 같다). */
    static String smoothPath(List<double[]> pts) {
        int n = pts.size();
        if (n == 0) return "";
        if (n == 1) return "M" + num(pts.get(0)[0]) + "," + num(pts.get(0)[1]);
        double[] dx = new double[n - 1], m = new double[n - 1], t = new double[n];
        for (int i = 0; i < n - 1; i++) {
            dx[i] = pts.get(i + 1)[0] - pts.get(i)[0];
            m[i] = (pts.get(i + 1)[1] - pts.get(i)[1]) / dx[i];
        }
        t[0] = m[0]; t[n - 1] = m[n - 2];
        for (int i = 1; i < n - 1; i++) {
            t[i] = m[i - 1] * m[i] <= 0 ? 0
                    : (3 * (dx[i - 1] + dx[i])) / ((2 * dx[i] + dx[i - 1]) / m[i - 1] + (dx[i] + 2 * dx[i - 1]) / m[i]);
        }
        StringBuilder d = new StringBuilder("M").append(num(pts.get(0)[0])).append(',').append(num(pts.get(0)[1]));
        for (int i = 0; i < n - 1; i++) {
            double h = dx[i] / 3;
            d.append(" C").append(num(pts.get(i)[0] + h)).append(',').append(num(pts.get(i)[1] + h * t[i]))
                    .append(' ').append(num(pts.get(i + 1)[0] - h)).append(',').append(num(pts.get(i + 1)[1] - h * t[i + 1]))
                    .append(' ').append(num(pts.get(i + 1)[0])).append(',').append(num(pts.get(i + 1)[1]));
        }
        return d.toString();
    }

    private static String num(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    /** 그래프 아래 요약 문장(화면 낭독기와 검색엔진도 읽는다). ActivityChart.tsx summary()와 같은 문장이다. */
    static String summary(KeywordDtos.Activity a, boolean en) {
        String label = switch (a.range()) {
            case "24h" -> en ? "Last 24 hours" : "최근 24시간";
            case "7d" -> en ? "Last 7 days" : "최근 7일";
            case "30d" -> en ? "Last 30 days" : "최근 30일";
            default -> en ? "Last 180 days" : "최근 180일";
        };
        long search = a.points().stream().mapToLong(KeywordDtos.ActivityPoint::search).sum();
        long plus = a.points().stream().mapToLong(KeywordDtos.ActivityPoint::plus).sum();
        if (search + plus == 0) {
            return en ? String.format(Locale.ROOT, "%s: no activity. Current holders: %,d.", label, a.holders())
                    : String.format(Locale.ROOT, "%s 동안 기록이 없어요. 지금 %,d명이 가지고 있어요.", label, a.holders());
        }
        KeywordDtos.ActivityPoint best = a.points().get(0);
        for (KeywordDtos.ActivityPoint p : a.points()) if (p.search() + p.plus() > best.search() + best.plus()) best = p;
        Fmt f = new Fmt(a.timezone(), a.range(), en);
        String when = en ? "at " + f.when(best) : f.when(best) + "에";
        return en ? String.format(Locale.ROOT, "%s: searches %,d, adds %,d, busiest %s.", label, search, plus, when)
                : String.format(Locale.ROOT, "%s 검색 %,d회, 추가 %,d회, %s 가장 많았어요.", label, search, plus, when);
    }

    /** 나라 시간대와 언어에 맞춘 날짜·시각 글자. */
    private static final class Fmt {
        private final ZoneId zone;
        private final boolean hourly, en;
        private final DateTimeFormatter tick, full, hour;

        Fmt(String timezone, String range, boolean en) {
            this.zone = ZoneId.of(timezone); this.en = en; this.hourly = "24h".equals(range);
            Locale loc = en ? Locale.ENGLISH : Locale.KOREAN;
            this.tick = DateTimeFormatter.ofPattern(hourly ? (en ? "h a" : "H시") : "180d".equals(range) ? (en ? "MMM" : "M월") : "M/d", loc).withZone(zone);
            this.full = DateTimeFormatter.ofPattern(en ? "MMMM d, EEE" : "M월 d일 (E)", loc).withZone(zone);
            this.hour = DateTimeFormatter.ofPattern(en ? "h a" : "H시", loc).withZone(zone);
        }

        String tick(KeywordDtos.ActivityPoint p) { return tick.format(p.t()); }
        String when(KeywordDtos.ActivityPoint p) { return hourly ? hour.format(p.t()) : full.format(p.t()); }
    }
}
