package com.trendist.keyword.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 제미나이 REST(generateContent) 호출(10.4). API 키는 서버 환경 변수(dev는 DEV_GEMINI_API_KEY, prod는 PROD_GEMINI_API_KEY)에만 두고 브라우저로 보내지 않는다.
 * 키가 비어 있으면 {@link #enabled()}가 false라서 AI 설명 기능이 조용히 꺼진다.
 */
@Component
public class GeminiClientImpl implements GeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClientImpl.class);
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    /** 설명 한 줄의 최대 글자 수(키워드 카드와 DB 칸 크기) */
    static final int MAX_LEN = 200;

    private static final String SYSTEM = """
            너는 한국어 실시간 트렌드 게임 'Trendist'의 설명 도우미야.
            사용자가 준 검색어(키워드)가 '지금 왜 화제인지·왜 유행인지'를 한국어 한 문장으로 알려줘.
            사전식 뜻풀이("~는 ~을 뜻하는 말")만 쓰지 말고, 지금 무슨 일이 있어서 사람들이 찾는지(사건·발표·방송·경기·출시·논란·밈 등)를 담아.
            키워드마다 관련 뉴스 제목이 붙어 있으면 그 제목들만 근거로 이유를 써. 제목에 없는 내용을 지어내지 마.
            뉴스 제목이 없으면 네가 확실히 아는 경우에만 쓰고, 최근 일은 네 지식에 없을 수 있으니 확실하지 않으면 UNKNOWN 이라고 적어.
            규칙: 키워드마다 한 문장, 90자 이내, 존댓말(~예요/~해요/~했어요).
            확인되지 않은 추측, 루머, 특정인에 대한 비방·사생활, 선정적·혐오 표현은 쓰지 마. 사건·사고는 사실만 중립적으로 써.
            광고 문구, 링크, 이모지, 따옴표, 마크다운은 쓰지 마. 뉴스 제목을 그대로 베끼지 말고 풀어서 써.
            좋은 예: 키워드 '장성규' + 제목 '장성규, 나혼산 김민에 팬심 고백...' → 방송인 장성규 씨가 '나 혼자 산다' 출연자에게 팬심을 밝힌 일이 화제가 됐어요.
            나쁜 예(뜻풀이만 있음): 방송인 장성규 씨를 가리키는 이름이에요.
            키워드 목록을 번호와 함께 줄 거야. 입력된 모든 번호에 대해 JSON 배열 [{"no": 번호, "comment": "설명 또는 UNKNOWN"}]로만 답해.""";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;

    public GeminiClientImpl(ObjectMapper json, @Value("${trendist.gemini.api-key:}") String apiKey,
                            @Value("${trendist.gemini.model:gemini-3.5-flash-lite}") String model) {
        this.json = json; this.apiKey = apiKey == null ? "" : apiKey.trim(); this.model = model;
    }

    /** 시작할 때 키가 들어왔는지만 남긴다(키 값은 로그에 남기지 않는다) */
    @jakarta.annotation.PostConstruct
    void logStatus() { log.info("gemini: {} (model {})", enabled() ? "API 키 있음, AI 설명 켜짐" : "API 키 없음, AI 설명 꺼짐", model); }

    @Override
    public boolean enabled() { return !apiKey.isEmpty(); }

    @Override
    public Map<String, Optional<String>> describeAll(List<String> keywords, Map<String, List<String>> news, boolean grounding) throws Exception {
        if (!enabled()) throw new IllegalStateException("제미나이 API 키(DEV_/PROD_GEMINI_API_KEY)가 설정되지 않았어요");
        if (keywords.isEmpty()) return Map.of();
        StringBuilder list = new StringBuilder("키워드 목록:\n");
        for (int i = 0; i < keywords.size(); i++) {
            list.append(i + 1).append(". ").append(keywords.get(i)).append('\n');
            for (String h : news.getOrDefault(keywords.get(i), List.of())) list.append("   - 뉴스 제목: ").append(h).append('\n');
        }
        Map<String, Object> schema = Map.of("type", "ARRAY", "items", Map.of("type", "OBJECT",
                "properties", Map.of("no", Map.of("type", "INTEGER"), "comment", Map.of("type", "STRING")),
                "required", List.of("no", "comment")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM))));
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", list.toString())))));
        body.put("generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", 4096,
                "responseMimeType", "application/json", "responseSchema", schema));
        if (grounding) body.put("tools", List.of(Map.of("google_search", Map.of())));
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + model + ":generateContent"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 429) throw new RateLimitedException("gemini 429");
        if (res.statusCode() != 200) {
            log.warn("gemini HTTP {}: {}", res.statusCode(), res.body().length() > 300 ? res.body().substring(0, 300) : res.body());
            throw new IllegalStateException("gemini HTTP " + res.statusCode());
        }
        JsonNode parts = json.readTree(res.body()).path("candidates").path(0).path("content").path("parts");
        StringBuilder text = new StringBuilder();
        for (JsonNode p : parts) text.append(p.path("text").asString(""));
        return parse(text.toString(), keywords);
    }

    /** 응답 JSON 배열([{no, comment}])을 입력 순서(no는 1부터)에 맞춰 키워드별 답으로 바꾼다. 범위 밖 번호·깨진 항목은 버린다 */
    Map<String, Optional<String>> parse(String raw, List<String> keywords) {
        Map<String, Optional<String>> out = new LinkedHashMap<>();
        JsonNode arr;
        try { arr = json.readTree(raw); } catch (Exception e) { return out; }
        if (arr == null || !arr.isArray()) return out;
        for (JsonNode item : arr) {
            int no = item.path("no").asInt(0);
            if (no < 1 || no > keywords.size() || !item.has("comment")) continue;
            out.put(keywords.get(no - 1), clean(item.path("comment").asString("")));
        }
        return out;
    }

    /** 답을 한 줄로 다듬는다. 비었거나 UNKNOWN이거나 링크가 섞였거나 너무 길면 빈 값 */
    static Optional<String> clean(String raw) {
        if (raw == null) return Optional.empty();
        String s = raw.strip().lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("");
        s = s.replaceAll("[*_`#>\"“”]", "").replaceAll("\\s+", " ").strip();
        if (s.isEmpty() || s.toUpperCase().contains("UNKNOWN") || s.contains("http") || s.contains("www.")) return Optional.empty();
        if (s.length() > MAX_LEN) return Optional.empty();
        return Optional.of(s);
    }
}
