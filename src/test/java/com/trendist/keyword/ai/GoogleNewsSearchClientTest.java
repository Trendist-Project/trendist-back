package com.trendist.keyword.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GoogleNewsSearchClientTest {
    @Test
    void keepsOnlyTitlesWithKeywordAndStripsPublisher() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>x</title>
                  <item><title>두쫀쿠 열풍, 품절 대란 - 한국경제</title></item>
                  <item><title>전혀 다른 기사 - 중앙일보</title></item>
                  <item><title>두 쫀쿠 가격 논란 - 연합뉴스</title></item>
                </channel></rss>""";
        assertThat(GoogleNewsSearchClient.parse(xml.getBytes(StandardCharsets.UTF_8), "두쫀쿠"))
                .containsExactly("두쫀쿠 열풍, 품절 대란", "두 쫀쿠 가격 논란");
    }

    @Test
    void disabledReturnsEmpty() { assertThat(new GoogleNewsSearchClient(false).search("아무거나", "ko")).isEmpty(); }
}
