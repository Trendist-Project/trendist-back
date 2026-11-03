package com.trendist.keyword.ai;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 구글 뉴스 검색 RSS(키 없이 무료)로 키워드의 최근 7일 뉴스 제목을 찾는다. 제목에 키워드가 들어 있는 것만 쓴다(엉뚱한 결과로 지어내지 않게).
 * 실패하거나 결과가 없으면 빈 목록이라서 AI 설명은 "모름"이 되고, 관리자가 직접 쓴다.
 */
@Component
public class GoogleNewsSearchClient implements NewsSearchClient {
    private static final Logger log = LoggerFactory.getLogger(GoogleNewsSearchClient.class);
    private static final String URL = "https://news.google.com/rss/search?hl=ko&gl=KR&ceid=KR:ko&q=";
    static final int MAX_HEADLINES = 5;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final boolean enabled;

    public GoogleNewsSearchClient(@Value("${trendist.news-search.enabled:true}") boolean enabled) { this.enabled = enabled; }

    @Override
    public List<String> search(String keyword) {
        if (!enabled || keyword == null || keyword.isBlank()) return List.of();
        try {
            String q = URLEncoder.encode(keyword.strip() + " when:7d", StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL + q)).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (compatible; Trendist/1.0)").GET().build();
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) { log.warn("news search HTTP {}", res.statusCode()); return List.of(); }
            return parse(res.body(), keyword);
        } catch (Exception e) {
            log.warn("news search failed: {}", e.toString());
            return List.of();
        }
    }

    /** item/title에서 " - 매체" 꼬리를 떼고, 키워드(공백·대소문자 무시)가 들어 있는 제목만 최대 5개. */
    static List<String> parse(byte[] xml, String keyword) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setExpandEntityReferences(false);
        NodeList items = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getElementsByTagName("item");
        String key = squash(keyword);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < items.getLength() && out.size() < MAX_HEADLINES; i++) {
            NodeList t = ((Element) items.item(i)).getElementsByTagName("title");
            if (t.getLength() == 0) continue;
            String title = t.item(0).getTextContent().strip();
            int cut = title.lastIndexOf(" - ");
            if (cut > 0) title = title.substring(0, cut).strip();
            if (!title.isEmpty() && squash(title).contains(key) && !out.contains(title)) out.add(title);
        }
        return out;
    }

    private static String squash(String s) { return s.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT); }
}
