package com.trendist.share.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.share.dto.ShareDtos;
import com.trendist.share.service.ShareCardService;
import com.trendist.share.support.SharePage;
import com.trendist.share.support.ShareViewCounter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 자랑하기 카드 공유 주소(4.24, 9.6). 만들기는 로그인(게스트 포함), 공유 페이지와 이미지는 누구나 본다. */
@RestController
@RequiredArgsConstructor
public class ShareCardController {
    private static final MediaType HTML = new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8);

    private final ShareCardService service;

    @PostMapping("/api/share-cards")
    public ShareDtos.Created create(@AuthenticationPrincipal AuthUser user) {
        return service.create(user.userId());
    }

    /** 열 때마다 조회 수를 세야 하므로 브라우저·프록시가 캐시하지 않게 한다(no-cache, 4.24) */
    @GetMapping("/share/{code}")
    public ResponseEntity<String> page(@PathVariable String code, HttpServletRequest req) {
        return service.find(code)
                .map(d -> {
                    service.count(code, ShareViewCounter.Kind.VIEW, req.getRemoteAddr(), req.getHeader(HttpHeaders.USER_AGENT));
                    return ResponseEntity.ok().contentType(HTML).cacheControl(CacheControl.noCache())
                            .body(SharePage.html(d, service.baseUrl(), code));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(HTML).body(SharePage.notFound(service.baseUrl())));
    }

    /** [나도 플레이 하기]: 클릭을 세고 메인 화면으로 보낸다. 없는 코드도 메인으로 */
    @GetMapping("/share/{code}/play")
    public ResponseEntity<Void> play(@PathVariable String code, HttpServletRequest req) {
        service.count(code, ShareViewCounter.Kind.PLAY, req.getRemoteAddr(), req.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create(service.baseUrl() + "/?ref=share"))
                .cacheControl(CacheControl.noStore()).build();
    }

    /** 같은 코드의 그림은 바뀌지 않으므로 1년 캐시한다(immutable) */
    @GetMapping("/share/{code}/card.png")
    public ResponseEntity<byte[]> image(@PathVariable String code) {
        return service.image(code)
                .map(png -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(png))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
