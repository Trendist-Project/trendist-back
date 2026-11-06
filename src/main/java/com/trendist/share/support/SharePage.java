package com.trendist.share.support;

import com.trendist.share.dto.ShareDtos;

/**
 * 공유 페이지 HTML(4.24). 메신저 크롤러는 JavaScript를 실행하지 않으므로 OG 태그를 서버가 바로 넣는다.
 * 닉네임·칭호는 사용자 값이라 모두 HTML 이스케이프한다.
 */
public final class SharePage {
    private SharePage() {}

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    public static String title(ShareDtos.CardData d) {
        return d.english() ? d.nickname() + "'s Trend Report" : d.nickname() + "의 트렌드 성적표";
    }

    public static String description(ShareDtos.CardData d) {
        if (d.english()) {
            return String.format(java.util.Locale.ENGLISH, "Trend score %,d · Lv. %d%s. Know the trend. Grow bigger!", d.score(), d.level(),
                    d.rank() != null ? " · Rank #" + d.rank() + " now" : "");
        }
        return String.format("유행도 %,d · %d레벨%s. 트랜드를 아는 자, 커진다!", d.score(), d.level(),
                d.rank() != null ? " · 지금 " + d.rank() + "위" : "");
    }

    public static String html(ShareDtos.CardData d, String baseUrl, String code) {
        String url = baseUrl + "/share/" + code, image = url + "/card.png", play = url + "/play";
        String t = escape(title(d)), desc = escape(description(d));
        return """
                <!doctype html>
                <html lang="%6$s">
                <head>
                %9$s
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="robots" content="index,follow">
                <title>%1$s · Trendist</title>
                <meta name="description" content="%2$s">
                <meta property="og:type" content="website">
                <meta property="og:site_name" content="Trendist">
                <meta property="og:title" content="%1$s">
                <meta property="og:description" content="%2$s">
                <meta property="og:url" content="%3$s">
                <meta property="og:image" content="%4$s">
                <meta property="og:image:width" content="1080">
                <meta property="og:image:height" content="1350">
                <meta property="og:image:alt" content="%1$s">
                <meta name="twitter:card" content="summary_large_image">
                <meta name="twitter:title" content="%1$s">
                <meta name="twitter:description" content="%2$s">
                <meta name="twitter:image" content="%4$s">
                <link rel="canonical" href="%3$s">
                <link rel="icon" href="/favicon.ico" sizes="16x16 32x32 48x48">
                <link rel="icon" type="image/png" sizes="48x48" href="/favicon-48.png">
                <link rel="icon" type="image/png" sizes="96x96" href="/favicon-96.png">
                <style>
                  body{margin:0;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:20px;
                    padding:24px 16px;box-sizing:border-box;background:#0d1420;color:#e8eef6;font-family:system-ui,-apple-system,"Noto Sans KR",sans-serif}
                  img{width:min(100%%,432px);height:auto;border-radius:16px;box-shadow:0 12px 40px rgba(0,0,0,.45)}
                  a.play{display:inline-block;padding:14px 28px;border-radius:12px;background:#3fb68b;color:#0d1420;font-weight:800;
                    font-size:1.05rem;text-decoration:none;transition:background .15s,transform .15s,box-shadow .15s}
                  a.play:hover{background:#5fd3a8;transform:translateY(-2px);box-shadow:0 6px 18px rgba(63,182,139,.35)}
                  a.play:active{background:#35a07b;transform:translateY(0);box-shadow:none}
                  a.play:focus-visible{outline:3px solid #9fe0c3;outline-offset:3px}
                  p{margin:0;color:#8b9bb0;font-size:.9rem}
                </style>
                </head>
                <body>
                %10$s
                <img src="%4$s" width="1080" height="1350" alt="%1$s">
                <a class="play" href="%5$s">%7$s</a>
                <p>%8$s</p>
                </body>
                </html>
                """.formatted(t, desc, escape(url), escape(image), escape(play), d.english() ? "en" : "ko",
                        d.english() ? "Play now" : "플레이 하러 가기",
                        d.english() ? "Know the trend. Grow bigger. Trendist" : "트랜드를 아는 자, 커진다. Trendist",
                        com.trendist.common.support.AnalyticsTag.HEAD, com.trendist.common.support.AnalyticsTag.BODY);
    }

    public static String notFound(String baseUrl) {
        return """
                <!doctype html>
                <html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Trendist</title><meta name="robots" content="noindex"></head>
                <body style="margin:0;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;background:#0d1420;color:#e8eef6;font-family:system-ui,sans-serif">
                <p>카드를 찾을 수 없어요 · Card not found</p><a href="%s/" style="color:#3fb68b">Trendist</a>
                </body></html>
                """.formatted(escape(baseUrl));
    }
}
