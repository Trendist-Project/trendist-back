package com.trendist.keyword.support;

import com.trendist.keyword.dto.KeywordDtos;
import com.trendist.share.support.SharePage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * 공개 키워드 페이지 HTML(4.28). 게임 안 키워드 팝업(`KeywordModal.tsx`)과 같은 구성·같은 CSS다:
 * 제목 줄, AI 한 줄 설명, 상태·점수 영역, 통계 칸, 기간 탭이 있는 활동 그래프.
 * 검색엔진은 JavaScript를 실행하지 않을 수 있어 서버가 완성된 HTML을 바로 주고, 기간 탭·가진 사람 토글은 JavaScript 없이 CSS만으로 동작한다.
 * 키워드 이름과 설명은 사용자 입력이라 모두 HTML 이스케이프한다.
 */
public final class KeywordPage {
    private KeywordPage() {}

    /** 페이지에 그릴 값. views는 저장된 값에 아직 저장 전인 값을 더한 것, activities는 24h·7d·30d·180d 순서 */
    public record Data(String countryCode, String text, String normalized, String state, long trendValue,
                       long holders, long totalPlus, long searches, long views, String aiComment,
                       boolean googleSource, Map<String, KeywordDtos.Activity> activities) {
        public boolean english() { return !"KR".equalsIgnoreCase(countryCode); }
        public boolean active() { return "활성".equals(state); }
    }

    /** `/kr/keywords/키워드`: 나라 코드가 앞, 키워드는 정규화 키를 퍼센트 인코딩해서 쓴다. */
    public static String url(String baseUrl, String countryCode, String normalized) {
        return baseUrl + "/" + countryCode.toLowerCase(Locale.ROOT) + "/keywords/"
                + URLEncoder.encode(normalized, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String title(Data d) {
        return d.english() ? d.text() + " - trend & meaning | Trendist" : d.text() + " 뜻과 트렌드 | Trendist";
    }

    public static String description(Data d) {
        if (d.aiComment() != null && !d.aiComment().isBlank()) return d.aiComment();
        return d.english()
                ? String.format(Locale.ENGLISH, "See how \"%s\" is trending on Trendist: %,d holders, %,d adds.", d.text(), d.holders(), d.totalPlus())
                : String.format("Trendist에서 '%s' 키워드의 트렌드를 확인하세요. 가진 사람 %,d명, 누적 추가 %,d회.", d.text(), d.holders(), d.totalPlus());
    }

    /** 게임 화면의 스타일(styles.css)에서 키워드 팝업에 쓰는 규칙만 옮긴 것. 게임 CSS를 바꾸면 같이 맞춘다. */
    private static final String CSS = """
            :root{color-scheme:dark;--bg:#0b0f17;--panel:#141b26;--line:#273244;--text:#e8edf5;--muted:#94a3b8;--accent:#3fb68b;--warn:#ffb020;
              font-family:"Noto Sans KR",system-ui,-apple-system,sans-serif;color:var(--text);background:var(--bg)}
            *{box-sizing:border-box}
            body{margin:0;min-height:100vh;background:var(--bg)}
            a{color:inherit}
            .page{min-height:100vh;display:grid;grid-template-columns:minmax(0,1fr);justify-items:center;padding:16px;align-content:center;gap:14px}
            .brand{display:flex;align-items:center;gap:8px;text-decoration:none;font-weight:800;font-size:1.1rem;width:min(760px,100%)}
            .brand img{width:28px;height:28px}
            .muted{color:var(--muted)}.small{font-size:.85rem}
            .modal{width:min(760px,100%);display:flex;flex-direction:column;background:var(--panel);border:1px solid var(--line);border-radius:16px}
            .modal-head{display:flex;align-items:center;justify-content:space-between;padding:14px 18px;border-bottom:1px solid var(--line)}
            .modal-title{display:flex;align-items:center;gap:10px;min-width:0}
            .modal-title h1{margin:0;font-size:1.35rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .icon-button{width:32px;height:32px;padding:0;display:inline-grid;place-items:center;border-radius:50%;font-weight:700;border:1px solid var(--line);background:#1d2635;color:var(--text);text-decoration:none}
            .modal-body{padding:16px 18px;display:grid;grid-template-columns:minmax(0,1fr);gap:12px;align-content:start}
            .badge{font-size:.8rem;background:#1d2635;padding:2px 8px;border-radius:999px;white-space:nowrap}
            .badge.state-활성{border:1px solid rgba(255,140,80,.6);color:#ffb48a}
            .badge.state-비활성{color:var(--muted)}
            .kw-ai{display:grid;gap:4px;padding:10px 14px;border-radius:12px;background:rgba(124,196,255,.08);border:1px solid rgba(124,196,255,.28)}
            .kw-ai-label{display:inline-flex;align-items:center;gap:5px;font-size:.78rem;font-weight:700;color:#7cc4ff}
            .kw-ai p{margin:0;line-height:1.5;overflow-wrap:anywhere}
            .kw-hero{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:16px 18px;border-radius:14px;
              background:linear-gradient(135deg,rgba(63,182,139,.16),rgba(90,169,230,.08) 60%,transparent);border:1px solid var(--line)}
            .kw-hero-main{display:grid;gap:4px;justify-items:start}
            .kw-score{display:flex;align-items:baseline;gap:4px}
            .kw-score strong{font-size:2.6rem;line-height:1;font-variant-numeric:tabular-nums}
            .kw-score span{color:var(--muted);font-weight:600}
            .kw-hero-side{display:grid;gap:6px;justify-items:end}
            .kw-plus{font-size:1.05rem;padding:10px 22px;border-radius:12px;background:var(--accent);border:1px solid var(--accent);color:#062014;font-weight:700;text-decoration:none;white-space:nowrap}
            .kw-stats{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin:0}
            .kw-stats div{border:1px solid var(--line);border-radius:12px;padding:10px 12px;background:#0f151f}
            .kw-stats dt{font-size:.78rem;color:var(--muted)}
            .kw-stats dd{margin:2px 0 0;font-size:1.1rem;font-weight:700;font-variant-numeric:tabular-nums}
            .kw-activity{border:1px solid var(--line);border-radius:14px;padding:12px 14px;background:#0f151f;display:grid;grid-template-columns:minmax(0,1fr);gap:8px}
            .kw-activity-head{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
            .kw-activity-head h2{margin:0;font-size:1rem;margin-right:auto}
            .chip-row{display:flex;gap:4px}
            .chip{padding:4px 10px;border-radius:999px;font-size:.8rem;color:var(--muted);border:1px solid var(--line);background:#1d2635;cursor:pointer}
            .toggle{display:inline-flex;align-items:center;gap:6px;color:var(--muted);cursor:pointer;font-size:.85rem}
            .toggle i{width:14px;height:14px;border-radius:3px;border:1px solid var(--muted);display:inline-block}
            .ctl{position:absolute;opacity:0;pointer-events:none}
            .rng{display:none;min-width:0;gap:6px}
            #r24:checked~.kw-activity .rng-24h,#r7:checked~.kw-activity .rng-7d,#r30:checked~.kw-activity .rng-30d,#r180:checked~.kw-activity .rng-180d{display:grid}
            #r24:checked~.kw-activity label[for=r24],#r7:checked~.kw-activity label[for=r7],#r30:checked~.kw-activity label[for=r30],#r180:checked~.kw-activity label[for=r180]
              {color:#062014;background:var(--accent);border-color:var(--accent);font-weight:700}
            #hold:checked~.kw-activity .toggle i{background:#f2b84b;border-color:#f2b84b}
            .ctl:focus-visible~.kw-activity .chip-row,.ctl:focus-visible~.kw-activity .toggle{outline:3px solid #7dd3fc;outline-offset:2px;border-radius:8px}
            .h-el{display:none}
            #hold:checked~.kw-activity .h-el{display:inline}
            .chart-empty{height:200px;display:grid;place-items:center;margin:0}
            .chart-svg{display:block;overflow:visible;width:100%;height:auto}
            .chart-grid{stroke:rgba(148,163,184,.14);stroke-width:1}
            .chart-axis{fill:var(--muted);font-size:11px;font-variant-numeric:tabular-nums}
            .chart-axis.holders{fill:#f2b84b}
            .chart-line{fill:none;stroke-width:2.2;stroke-linecap:round;stroke-linejoin:round}
            .chart-line.dashed{stroke-dasharray:7 5}
            .chart-line.live{stroke-dasharray:2 4;opacity:.8}
            .chart-line.holders{stroke-width:1.4;stroke-dasharray:1 4}
            .chart-reveal{animation:chart-reveal .9s ease-out both;transform-origin:left}
            @keyframes chart-reveal{from{transform:scaleX(0)}to{transform:scaleX(1)}}
            .chart-live-dot circle{animation:chart-blink 1.2s ease-in-out infinite}
            @keyframes chart-blink{50%{opacity:.25}}
            .chart-peak circle{fill:#fff;stroke:#0b0f17;stroke-width:2}
            .chart-peak text{fill:#fff;font-size:11px;font-weight:600}
            .chart-legend{display:flex;gap:14px;font-size:.8rem;color:var(--muted)}
            .chart-legend i{display:inline-block;vertical-align:middle;margin-right:6px;width:18px;height:0;border-top-width:2px}
            .chart-legend i.solid{border-top-style:solid}.chart-legend i.dash{border-top-style:dashed}.chart-legend i.dot{border-top-style:dotted}
            .chart-summary{margin:0;font-size:.88rem}
            .src,.foot{margin:0;color:var(--muted);font-size:.85rem}
            @media (max-width:760px){.kw-stats{grid-template-columns:1fr 1fr}.kw-score strong{font-size:2rem}.kw-hero{flex-wrap:wrap}}
            @media (prefers-reduced-motion:reduce){.chart-reveal,.chart-live-dot circle{animation:none}}
            """;

    private static final String GEMINI = "<svg class=\"gemini-icon\" width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" aria-hidden=\"true\" focusable=\"false\"><defs>"
            + "<linearGradient id=\"gem\" x1=\"3\" y1=\"20\" x2=\"21\" y2=\"4\" gradientUnits=\"userSpaceOnUse\"><stop offset=\"0\" stop-color=\"#4285f4\"/>"
            + "<stop offset=\"0.55\" stop-color=\"#9b72cb\"/><stop offset=\"1\" stop-color=\"#d96570\"/></linearGradient></defs>"
            + "<path fill=\"url(#gem)\" d=\"M12 24A14.3 14.3 0 0 0 0 12 14.3 14.3 0 0 0 12 0a14.3 14.3 0 0 0 12 12 14.3 14.3 0 0 0-12 12\"/></svg>";

    public static String html(Data d, String baseUrl) {
        boolean en = d.english();
        String url = SharePage.escape(url(baseUrl, d.countryCode(), d.normalized())), base = SharePage.escape(baseUrl);
        String title = SharePage.escape(title(d)), desc = SharePage.escape(description(d));
        String badge = d.active() ? (en ? "🔥Active" : "🔥활성") : (en ? "💤Inactive" : "💤비활성");
        String ai = d.aiComment() == null || d.aiComment().isBlank() ? ""
                : "<section class=\"kw-ai\" aria-label=\"" + (en ? "AI one-line summary" : "AI 한 줄 설명") + "\"><div><span class=\"kw-ai-label\">" + GEMINI + " "
                + (en ? "AI one-line summary" : "AI 한 줄 설명") + "</span></div><p>" + SharePage.escape(d.aiComment()) + "</p></section>";
        StringBuilder charts = new StringBuilder();
        for (KeywordDtos.Activity a : d.activities().values()) charts.append(ChartSvg.block(a, en));
        String attribution = d.googleSource() ? "<p class=\"src\">Data source: Google Trends</p>" : "";
        String[][] stats = {
                {en ? "Holders" : "가진 사람", en ? "👥 " + n(d.holders()) : "👥 " + n(d.holders()) + "명"},
                {en ? "Total adds" : "누적 추가", n(d.totalPlus())},
                {en ? "Total searches" : "누적 검색", n(d.searches())},
                {en ? "Page views" : "조회수", n(d.views())}};
        StringBuilder statsHtml = new StringBuilder();
        for (String[] s : stats) statsHtml.append("<div><dt>").append(s[0]).append("</dt><dd>").append(s[1]).append("</dd></div>");
        return """
                <!doctype html>
                <html lang="@@LANG@@">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="robots" content="index,follow">
                <title>@@TITLE@@</title>
                <meta name="description" content="@@DESC@@">
                <link rel="canonical" href="@@URL@@">
                <meta property="og:type" content="article">
                <meta property="og:site_name" content="Trendist">
                <meta property="og:title" content="@@TITLE@@">
                <meta property="og:description" content="@@DESC@@">
                <meta property="og:url" content="@@URL@@">
                <meta property="og:image" content="@@BASE@@/logo.png">
                <meta name="twitter:card" content="summary">
                <meta name="twitter:title" content="@@TITLE@@">
                <meta name="twitter:description" content="@@DESC@@">
                <link rel="icon" href="/favicon.ico">
                <style>@@CSS@@</style>
                </head>
                <body>
                <div class="page">
                <a class="brand" href="@@BASE@@/"><img src="/logo.png" alt="" width="28" height="28">Trendist</a>
                <main class="modal keyword-modal">
                <header class="modal-head">
                <div class="modal-title"><h1>@@KW@@</h1></div>
                <a class="icon-button" href="@@BASE@@/" aria-label="@@CLOSE@@">✕</a>
                </header>
                <div class="modal-body">
                @@AI@@
                <section class="kw-hero">
                <div class="kw-hero-main"><span class="badge state-@@STATE@@">@@BADGE@@</span>
                <div class="kw-score"><strong>@@SCORE@@</strong><span>@@UNIT@@</span></div></div>
                <div class="kw-hero-side"><a class="kw-plus" href="@@BASE@@/?ref=keyword">@@ADD@@</a></div>
                </section>
                <dl class="kw-stats">@@STATS@@</dl>
                <input class="ctl" type="radio" name="range" id="r24" checked aria-label="24h">
                <input class="ctl" type="radio" name="range" id="r7" aria-label="7d">
                <input class="ctl" type="radio" name="range" id="r30" aria-label="30d">
                <input class="ctl" type="radio" name="range" id="r180" aria-label="180d">
                <input class="ctl" type="checkbox" id="hold" aria-label="@@HOLDERS@@">
                <section class="kw-activity" aria-label="@@GRAPH@@">
                <div class="kw-activity-head"><h2>@@ACTIVITY@@</h2>
                <div class="chip-row" role="group" aria-label="@@PERIOD@@">
                <label class="chip" for="r24">@@R24@@</label><label class="chip" for="r7">@@R7@@</label><label class="chip" for="r30">@@R30@@</label><label class="chip" for="r180">@@R180@@</label>
                </div>
                <label class="toggle small" for="hold"><i></i>@@HOLDERS@@</label>
                </div>
                @@CHARTS@@
                </section>
                @@SRC@@
                <p class="foot">@@FOOT@@</p>
                </div>
                </main>
                </div>
                </body>
                </html>
                """
                .replace("@@CSS@@", CSS).replace("@@LANG@@", en ? "en" : "ko").replace("@@TITLE@@", title).replace("@@DESC@@", desc)
                .replace("@@URL@@", url).replace("@@BASE@@", base).replace("@@KW@@", SharePage.escape(d.text()))
                .replace("@@CLOSE@@", en ? "Close" : "닫기").replace("@@AI@@", ai).replace("@@STATE@@", d.active() ? "활성" : "비활성")
                .replace("@@BADGE@@", badge).replace("@@SCORE@@", String.valueOf(d.trendValue())).replace("@@UNIT@@", en ? "pts" : "점")
                .replace("@@ADD@@", en ? "Add in game" : "게임에서 추가").replace("@@STATS@@", statsHtml)
                .replace("@@HOLDERS@@", en ? "Holders" : "가진 사람").replace("@@GRAPH@@", en ? "Activity chart" : "활동 그래프")
                .replace("@@ACTIVITY@@", en ? "Activity" : "활동").replace("@@PERIOD@@", en ? "Period" : "기간")
                .replace("@@R24@@", en ? "24h" : "24시간").replace("@@R7@@", en ? "7d" : "7일")
                .replace("@@R30@@", en ? "30d" : "30일").replace("@@R180@@", en ? "180d" : "180일")
                .replace("@@CHARTS@@", charts).replace("@@SRC@@", attribution)
                .replace("@@FOOT@@", en ? "Know the trend. Grow bigger. Trendist" : "트랜드를 아는 자, 커진다. Trendist");
    }

    private static String n(long v) { return String.format(Locale.ROOT, "%,d", v); }

    /** 404 화면에 쓰는 스타일(팝업과 같은 색·모서리). 아이콘의 둥둥 떠다니는 움직임은 모션 줄이기 설정에서 멈춘다. */
    private static final String NF_CSS = """
            :root{color-scheme:dark;--bg:#0b0f17;--panel:#141b26;--line:#273244;--text:#e8edf5;--muted:#94a3b8;--accent:#3fb68b;
              font-family:"Noto Sans KR",system-ui,-apple-system,sans-serif;color:var(--text);background:var(--bg)}
            *{box-sizing:border-box}
            body{margin:0;min-height:100vh;background:radial-gradient(900px 500px at 50% -10%,rgba(63,182,139,.10),transparent 70%),var(--bg)}
            a{color:inherit}
            .page{min-height:100vh;display:grid;grid-template-columns:minmax(0,1fr);justify-items:center;align-content:center;padding:16px;gap:14px}
            .brand{display:flex;align-items:center;gap:8px;text-decoration:none;font-weight:800;font-size:1.1rem;width:min(560px,100%)}
            .brand img{width:28px;height:28px}
            .card{width:min(560px,100%);background:var(--panel);border:1px solid var(--line);border-radius:16px;padding:28px 24px 24px;text-align:center;display:grid;gap:14px;justify-items:center}
            .art{width:min(260px,70%);height:auto;overflow:visible}
            .art .a{animation:nf-float 6s ease-in-out infinite}.art .b{animation:nf-float 5s ease-in-out -2s infinite}.art .c{animation:nf-float 7s ease-in-out -4s infinite}
            @keyframes nf-float{50%{transform:translateY(-7px)}}
            .code{margin:0;font-size:.8rem;font-weight:700;letter-spacing:.14em;color:var(--accent)}
            h1{margin:0;font-size:1.5rem;line-height:1.3;word-break:keep-all}
            .lead{margin:0;color:var(--muted);line-height:1.6;max-width:36em;word-break:keep-all}
            .tried{display:inline-block;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;background:#0f151f;border:1px solid var(--line);border-radius:999px;padding:4px 12px;font-size:.9rem}
            .actions{display:flex;gap:10px;flex-wrap:wrap;justify-content:center;margin-top:4px}
            .btn{padding:12px 22px;border-radius:12px;font-weight:700;text-decoration:none;border:1px solid var(--line);background:#1d2635;color:var(--text)}
            .btn.primary{background:var(--accent);border-color:var(--accent);color:#062014}
            .btn:focus-visible{outline:3px solid #7dd3fc;outline-offset:2px}
            .hints{margin:6px 0 0;padding:12px 16px;list-style:none;width:100%;text-align:left;display:grid;gap:6px;background:#0f151f;border:1px solid var(--line);border-radius:12px;font-size:.88rem;color:var(--muted)}
            .hints li::before{content:"· ";color:var(--accent)}
            .foot{margin:0;color:var(--muted);font-size:.85rem}
            @media (prefers-reduced-motion:reduce){.art .a,.art .b,.art .c{animation:none}}
            """;

    /** 키워드를 찾을 수 없을 때의 화면(404, 색인 제외). tried는 주소에 적힌 값이라 이스케이프해서 짧게 보여 준다. */
    public static String notFound(String baseUrl, boolean en, String tried) {
        String base = SharePage.escape(baseUrl);
        String shown = tried == null ? "" : tried.strip();
        if (shown.codePointCount(0, shown.length()) > 24) shown = shown.substring(0, shown.offsetByCodePoints(0, 24)) + "…";
        String chip = shown.isEmpty() ? "" : "<span class=\"tried\">" + SharePage.escape(shown) + "</span>";
        String art = "<svg class=\"art\" viewBox=\"0 0 260 160\" role=\"img\" aria-label=\"" + (en ? "A lost circle with a question mark" : "물음표가 떠 있는 원") + "\">"
                + "<defs><radialGradient id=\"g1\" cx=\"35%\" cy=\"30%\" r=\"80%\"><stop offset=\"0\" stop-color=\"#6fe0b4\"/><stop offset=\"1\" stop-color=\"#2f9c75\"/></radialGradient>"
                + "<radialGradient id=\"g2\" cx=\"35%\" cy=\"30%\" r=\"80%\"><stop offset=\"0\" stop-color=\"#8fc7f2\"/><stop offset=\"1\" stop-color=\"#4a8fc7\"/></radialGradient>"
                + "<radialGradient id=\"g3\" cx=\"35%\" cy=\"30%\" r=\"80%\"><stop offset=\"0\" stop-color=\"#f7d58a\"/><stop offset=\"1\" stop-color=\"#d99a2b\"/></radialGradient></defs>"
                + "<g class=\"b\"><circle cx=\"46\" cy=\"112\" r=\"18\" fill=\"url(#g2)\"/></g>"
                + "<g class=\"c\"><circle cx=\"222\" cy=\"44\" r=\"13\" fill=\"url(#g3)\"/></g>"
                + "<g class=\"a\"><circle cx=\"130\" cy=\"80\" r=\"52\" fill=\"url(#g1)\"/><circle cx=\"130\" cy=\"80\" r=\"52\" fill=\"none\" stroke=\"#ffffff\" stroke-opacity=\".35\" stroke-width=\"2\"/>"
                + "<text x=\"130\" y=\"101\" text-anchor=\"middle\" font-size=\"64\" font-weight=\"800\" fill=\"#062014\">?</text></g></svg>";
        String[] hints = en
                ? new String[]{"Check the spelling. Spaces and capitals don't matter.", "Nobody has added this keyword on this server yet.", "Keywords on the other server (KR / Global) have different addresses."}
                : new String[]{"철자를 확인해 주세요. 띄어쓰기와 대소문자는 달라도 괜찮아요.", "이 서버에서 아직 아무도 추가하지 않은 키워드일 수 있어요.", "다른 서버(KR / Global)의 키워드는 주소가 달라요."};
        StringBuilder li = new StringBuilder();
        for (String h : hints) li.append("<li>").append(h).append("</li>");
        return """
                <!doctype html>
                <html lang="@@LANG@@">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="robots" content="noindex">
                <title>@@TITLE@@ | Trendist</title>
                <link rel="icon" href="/favicon.ico">
                <style>@@CSS@@</style>
                </head>
                <body>
                <div class="page">
                <a class="brand" href="@@BASE@@/"><img src="/logo.png" alt="" width="28" height="28">Trendist</a>
                <main class="card">
                @@ART@@
                <p class="code">404</p>
                <h1>@@TITLE@@</h1>
                <p class="lead">@@LEAD@@</p>
                @@CHIP@@
                <div class="actions"><a class="btn primary" href="@@BASE@@/?ref=notfound">@@PLAY@@</a><a class="btn" href="@@BASE@@/">@@HOME@@</a></div>
                <ul class="hints">@@HINTS@@</ul>
                </main>
                <p class="foot">@@FOOT@@</p>
                </div>
                </body>
                </html>
                """
                .replace("@@CSS@@", NF_CSS).replace("@@LANG@@", en ? "en" : "ko").replace("@@BASE@@", base).replace("@@ART@@", art)
                .replace("@@TITLE@@", en ? "We couldn't find that keyword" : "키워드를 찾을 수 없어요")
                .replace("@@LEAD@@", en ? "The keyword may not exist yet, or it can't be opened. Find a rising keyword in the game and add it first." : "아직 없는 키워드이거나 열 수 없는 키워드예요. 게임에서 뜨는 키워드를 찾아 먼저 추가해 보세요.")
                .replace("@@CHIP@@", chip).replace("@@PLAY@@", en ? "Play Trendist" : "Trendist 플레이하기").replace("@@HOME@@", en ? "Home" : "메인으로")
                .replace("@@HINTS@@", li).replace("@@FOOT@@", en ? "Know the trend. Grow bigger. Trendist" : "트랜드를 아는 자, 커진다. Trendist");
    }
}
