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

    public static String title(ShareDtos.CardData d) { return d.nickname() + "의 트렌드 성적표"; }

    public static String description(ShareDtos.CardData d) {
        return String.format("유행도 %,d · %d레벨%s. 트랜드를 아는 자, 커진다!", d.score(), d.level(),
                d.rank() != null ? " · 지금 " + d.rank() + "위" : "");
    }

    public static String html(ShareDtos.CardData d, String baseUrl, String code) {
        String url = baseUrl + "/share/" + code, image = url + "/card.png", play = url + "/play";
        String t = escape(title(d)), desc = escape(description(d));
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="robots" content="noindex">
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
                <link rel="icon" href="/favicon.ico">
                <style>
                  body{margin:0;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:20px;
                    padding:24px 16px;box-sizing:border-box;background:#0d1420;color:#e8eef6;font-family:system-ui,-apple-system,"Noto Sans KR",sans-serif}
                  img{width:min(100%%,432px);height:auto;border-radius:16px;box-shadow:0 12px 40px rgba(0,0,0,.45)}
                  a.play{display:inline-block;padding:14px 28px;border-radius:12px;background:#3fb68b;color:#0d1420;font-weight:800;
                    font-size:1.05rem;text-decoration:none}
                  p{margin:0;color:#8b9bb0;font-size:.9rem}
                </style>
                </head>
                <body>
                <img src="%4$s" width="1080" height="1350" alt="%1$s">
                <a class="play" href="%5$s">플레이 하러 가기</a>
                <p>트랜드를 아는 자, 커진다. Trendist</p>
                </body>
                </html>
                """.formatted(t, desc, escape(url), escape(image), escape(play));
    }

    public static String notFound(String baseUrl) {
        return """
                <!doctype html>
                <html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Trendist</title><meta name="robots" content="noindex"></head>
                <body style="margin:0;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;background:#0d1420;color:#e8eef6;font-family:system-ui,sans-serif">
                <p>카드를 찾을 수 없어요</p><a href="%s/" style="color:#3fb68b">Trendist로 가기</a>
                </body></html>
                """.formatted(escape(baseUrl));
    }
}
