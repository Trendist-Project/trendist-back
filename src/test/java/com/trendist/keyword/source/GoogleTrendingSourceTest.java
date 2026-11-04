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
    void readsRelatedNewsTitlesPerItem() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss xmlns:ht="https://trends.google.com/trending/rss" version="2.0"><channel>
                  <item><title>병무청</title>
                    <ht:news_item><ht:news_item_title>입영 일정 변경</ht:news_item_title></ht:news_item>
                    <ht:news_item><ht:news_item_title>병역 제도 개편</ht:news_item_title></ht:news_item></item>
                  <item><title>뉴스없음</title></item>
                </channel></rss>""";
        assertThat(GoogleTrendingSource.parseNews(xml.getBytes(StandardCharsets.UTF_8)))
                .containsOnlyKeys("병무청").containsEntry("병무청", java.util.List.of("입영 일정 변경", "병역 제도 개편"));
    }

    @Test
    void rejectsDoctype() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><rss><channel><item><title>&x;</title></item></channel></rss>";
        assertThatThrownBy(() -> GoogleTrendingSource.parseTitles(xml.getBytes(StandardCharsets.UTF_8))).isInstanceOf(Exception.class);
    }

    @Test
    void supportsKrAndGlobalOnlyAndKeepsListsPerCountry() {
        var src = new GoogleTrendingSource(java.time.Clock.systemUTC(), new com.trendist.keyword.ai.TrendNewsContext());
        assertThat(src.supports("KR")).isTrue();
        assertThat(src.supports("GLOBAL")).isTrue(); // GLOBAL은 미국(geo=US) 목록을 쓴다
        assertThat(src.supports("JP")).isFalse();
        assertThat(src.lastTitles("GLOBAL")).isEmpty();
        assertThat(src.fetchedAt("KR")).isNull();
        assertThatThrownBy(() -> src.refresh("JP")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void devSourceHasSeparateKoreanAndEnglishLists() {
        var props = new com.trendist.config.AppProperties.DevTrends(true, java.util.List.of("탕후루:35"), java.util.List.of("Dubai Chocolate:78"));
        var dev = new DevTrendSource(new com.trendist.config.AppProperties(null, null, null, props, null, null));
        assertThat(dev.supports("GLOBAL")).isTrue();
        assertThat(dev.heat("GLOBAL", "dubaichocolate")).hasValue(78); // 영어는 대소문자·공백 무시(정규화 키)
        assertThat(dev.heat("KR", "dubaichocolate")).isEmpty();        // 다른 나라 목록은 섞이지 않는다
        assertThat(dev.heat("KR", "탕후루")).hasValue(35);
        assertThat(dev.heat("GLOBAL", "탕후루")).isEmpty();
    }
}
