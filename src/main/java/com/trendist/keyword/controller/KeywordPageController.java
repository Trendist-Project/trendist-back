package com.trendist.keyword.controller;

import com.trendist.keyword.service.KeywordPageService;
import com.trendist.keyword.service.SitemapService;
import com.trendist.keyword.support.KeywordPage;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 키워드 페이지와 사이트맵 `/sitemap.xml`(4.28). 로그인 없이 누구나(검색엔진 포함) 본다.
 * 주소는 서버(나라)가 앞이다: `/kr/keywords/{키워드}`, `/global/keywords/{키워드}`.
 */
@RestController
@RequiredArgsConstructor
public class KeywordPageController {
    private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);
    private static final MediaType XML = new MediaType("application", "xml", StandardCharsets.UTF_8);

    private final KeywordPageService pages;
    private final SitemapService sitemaps;

    /** 열 때마다 조회수를 세야 하므로 브라우저·프록시가 캐시하지 않게 한다(no-cache) */
    @GetMapping("/{country:kr|global}/keywords/{slug}")
    public ResponseEntity<String> page(@PathVariable String country, @PathVariable String slug, HttpServletRequest req) {
        return pages.find(country, slug)
                .map(f -> ResponseEntity.ok().contentType(HTML).cacheControl(CacheControl.noCache())
                        .body(KeywordPage.html(pages.countAndShow(f, req.getRemoteAddr(), req.getHeader(HttpHeaders.USER_AGENT)), pages.baseUrl())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(HTML)
                        .body(KeywordPage.notFound(pages.baseUrl(), !"kr".equals(country), slug)));
    }

    /** 메인 주소와 모든 키워드 페이지를 한 파일로(5분 캐시). 프론트 public/sitemap.xml 대신 운영 nginx가 이 주소를 백엔드로 넘긴다 */
    @GetMapping("/sitemap.xml")
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok().contentType(XML).cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic()).body(sitemaps.sitemap());
    }
}
