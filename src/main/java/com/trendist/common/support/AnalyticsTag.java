package com.trendist.common.support;

/**
 * 서버가 그리는 공개 페이지(키워드 페이지 4.28, 공유 페이지 4.24)에 넣는 Google 태그 관리자(GTM) 표준 코드.
 * 메인 `index.html`과 같은 컨테이너를 써서 방문이 한 곳에 모인다. 관리자 화면에는 넣지 않는다.
 * `<head>` 맨 위에 {@link #HEAD}, `<body>` 바로 다음에 {@link #BODY}를 둔다.
 */
public final class AnalyticsTag {
    public static final String CONTAINER_ID = "GTM-WRFP5PLN";

    public static final String HEAD = "<script>(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':new Date().getTime(),event:'gtm.js'});"
            + "var f=d.getElementsByTagName(s)[0],j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;"
            + "j.src='https://www.googletagmanager.com/gtm.js?id='+i+dl;f.parentNode.insertBefore(j,f);})(window,document,'script','dataLayer','"
            + CONTAINER_ID + "');</script>";

    public static final String BODY = "<noscript><iframe src=\"https://www.googletagmanager.com/ns.html?id=" + CONTAINER_ID
            + "\" height=\"0\" width=\"0\" style=\"display:none;visibility:hidden\"></iframe></noscript>";

    private AnalyticsTag() {}
}
