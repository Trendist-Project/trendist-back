package com.trendist.keyword.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GoogleTrendingSourceTest {

    @Test
    void readsItemTitlesOnly() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss xmlns:ht="https://trends.google.com/trending/rss" version="2.0"><channel>
                  <title>Daily Search Trends</title>
                  <item><title>병무청</title><ht:approx_traffic>1000+</ht:approx_traffic>
                    <ht:news_item><ht:news_item_title>뉴스 제목</ht:news_item_title></ht:news_item></item>
                  <item><title>콜 오브 듀티: 모던 워페어 4</title></item>
                </channel></rss>""";
        assertThat(GoogleTrendingSource.parseTitles(xml.getBytes(StandardCharsets.UTF_8)))
                .containsExactly("병무청", "콜 오브 듀티: 모던 워페어 4"); // 채널 제목과 뉴스 제목은 넣지 않는다
    }

    @Test
    void rejectsDoctype() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><rss><channel><item><title>&x;</title></item></channel></rss>";
        assertThatThrownBy(() -> GoogleTrendingSource.parseTitles(xml.getBytes(StandardCharsets.UTF_8))).isInstanceOf(Exception.class);
    }
}
