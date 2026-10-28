package com.trendist.keyword.source;

import com.trendist.keyword.entity.DataSource;
import com.trendist.keyword.support.KeywordNormalizer;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 구글 트렌드 실시간 인기 RSS(10.1). 목록의 제목(title)을 그대로 키워드 후보로 쓴다.
 * 5분마다 다시 읽고(구글은 약 10분 단위로 바뀐다), 마지막으로 읽은 목록에 있는 단어는 외부 확인으로 본다.
 */
@Component
@ConditionalOnProperty(prefix = "trendist.google-trends", name = "enabled", havingValue = "true")
public class GoogleTrendingSource implements ExternalTrendSource {
    private static final String RSS_URL = "https://trends.google.com/trending/rss?geo=";
    private static final Duration FRESH_FOR = Duration.ofMinutes(30); // 이보다 오래 못 읽었으면 목록을 모르는 것으로 본다

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Clock clock;
    private volatile Set<String> current = Set.of();
    private volatile Instant fetchedAt;

    public GoogleTrendingSource(Clock clock) { this.clock = clock; }

    @Override public boolean supports(String countryCode) { return "KR".equals(countryCode); }
    @Override public String sourceName() { return DataSource.GOOGLE; }

    /** 마지막 목록에 있으면 100, 없으면 값 없음. 목록이 오래됐으면 값 없음. */
    @Override
    public OptionalDouble heat(String countryCode, String normalized) {
        Set<String> now = currentTitles();
        return now != null && now.contains(normalized) ? OptionalDouble.of(100) : OptionalDouble.empty();
    }

    /** 최근(30분 안)에 읽은 목록의 정규화 키. 모르면 null. */
    public Set<String> currentTitles() {
        Instant at = fetchedAt;
        return at != null && at.plus(FRESH_FOR).isAfter(Instant.now(clock)) ? current : null;
    }

    /** RSS를 읽어 제목 목록(표기 그대로)을 돌려주고 현재 목록을 바꾼다. 실패하면 예외. */
    public List<String> refresh(String countryCode) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(RSS_URL + countryCode)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 (compatible; Trendist/1.0)").GET().build();
        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) throw new IllegalStateException("google trends rss HTTP " + res.statusCode());
        List<String> titles = parseTitles(res.body());
        current = titles.stream().map(KeywordNormalizer::normalize).collect(Collectors.toUnmodifiableSet());
        fetchedAt = Instant.now(clock);
        return titles;
    }

    /** item/title만 꺼낸다. 외부 엔티티·DTD는 막는다(XXE 방지). */
    static List<String> parseTitles(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setExpandEntityReferences(false);
        NodeList items = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getElementsByTagName("item");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) {
            NodeList t = ((Element) items.item(i)).getElementsByTagName("title");
            if (t.getLength() > 0) {
                String title = t.item(0).getTextContent();
                if (title != null && !title.isBlank()) out.add(title.strip());
            }
        }
        return out;
    }
}
