package com.trendist.integration.service;

import com.trendist.integration.entity.IntegrationLink;
import com.trendist.integration.repository.IntegrationLinkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 연동 바로가기 기본 4개(13.15). 없는 것만 채우므로 관리자가 고친 주소는 그대로 둔다(11.3). */
@Component
@RequiredArgsConstructor
public class IntegrationSeeder implements ApplicationRunner {
    public record Def(String code, String label, String url) {}

    public static final List<Def> DEFAULTS = List.of(
            new Def("NAVER_SEARCH_ADVISOR", "네이버 서치어드바이저", "https://searchadvisor.naver.com/console/site/summary?site=https%3A%2F%2Ftrendist.kr"),
            new Def("GOOGLE_SEARCH_CONSOLE", "구글 서치 콘솔", "https://search.google.com/u/1/search-console?resource_id=https%3A%2F%2Ftrendist.kr%2F&hl=ko"),
            new Def("GOOGLE_ANALYTICS", "구글 애널리틱스", "https://analytics.google.com/analytics/web/?authuser=1#/a414657819p562273206/reports/intelligenthome"),
            new Def("GOOGLE_AI_STUDIO", "구글 AI 스튜디오 - 제미나이", "https://aistudio.google.com/api-keys"));

    private final IntegrationLinkRepository links;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (int i = 0; i < DEFAULTS.size(); i++) {
            Def d = DEFAULTS.get(i);
            if (links.findByCode(d.code()).isEmpty()) links.save(new IntegrationLink(d.code(), d.label(), d.url(), i + 1));
        }
    }
}
