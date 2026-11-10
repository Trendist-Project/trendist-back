package com.trendist.keyword.service;

import com.trendist.keyword.dto.KeywordDtos;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 검색, +, 보관함, 만료, 충돌로 잃기(4.4~4.8, 9.4). */
public interface KeywordService {
    KeywordDtos.SearchResult search(long userId, String q);

    KeywordDtos.PlusResult plus(long userId, String rawText);

    List<KeywordDtos.MyKeyword> myKeywords(long userId);

    /** 특정 플레이어의 보관함(관리자 사용자 상세가 나라를 골라 볼 때). */
    List<KeywordDtos.MyKeyword> myKeywordsOfPlayer(long playerId);

    /**
     * 키워드 상세 카드(4.12). 내 나라 키워드만, 금지 키워드는 볼 수 없다. 팝업을 연 것도 조회수에 센다(4.28):
     * 공개 페이지와 같은 규칙이라 같은 방문자(ip + userAgent)는 키워드마다 하루 1번이고 크롤러는 세지 않는다. 돌려주는 카드의 조회수에는 이번 조회도 들어 있다.
     */
    KeywordDtos.KeywordCard detail(long userId, long keywordId, String ip, String userAgent);

    /** 방 입장 때 보유 색인에 넣을 키워드 번호(만료 전, 금지 아님). */
    List<Long> activeKeywordIds(long playerId);

    /** 만료된 + 하나를 지우고 인원·트렌드 값·점수를 고친다. 지웠으면 true. */
    boolean expireOne(long playerKeywordId);

    /** 충돌로 잃은 키워드: 이름과 잃는 순간의 키워드 점수(💤면 0). 이름은 이긴 쪽에만, 점수는 양쪽에 보여 준다(4.3). */
    record LostKeyword(long keywordId, String text, long score) {}
    /** 봇이 가질 수 있는 🔥 키워드 한 줄(번호, 이름, 지금 유행도, 4.21) */
    record BotKeyword(long id, String text, long score) {}
    /** 키워드를 모두 잃은 결과: 잃은 개수, 그중 🔥활성이던 개수(! 구슬 수의 기준), 점수 합 */
    record LostAll(int count, int activeCount, long score) {}

    /** 충돌로 잃기(4.3): 금지되지 않은 키워드 중 하나를 무작위로 지운다. */
    Optional<LostKeyword> loseRandom(long playerId);

    /** 불꽃에게 져서 가진 키워드를 모두 잃는다(금지 제외, 🔥·💤 무관, 4.25). 잃은 것이 없으면 빈 값. */
    Optional<LostAll> loseAll(long playerId);

    /**
     * 가진 키워드의 일부를 잃는다(체력 0으로 죽을 때·불꽃에게 부딪혔을 때): 금지되지 않은 키워드 중 max(1, 개수 × percent%의 내림)개를 무작위로 지운다.
     * percent가 100 이상이면 모두 잃는다(loseAll과 같다). 잃은 것이 없으면 빈 값.
     */
    Optional<LostAll> loseFraction(long playerId, int percent);

    /**
     * 빼앗긴 키워드(4.3). result: ADDED(이긴 사람 보관함에 새로 들어감) / RENEWED(이미 가진 키워드라 남은 시간만 24시간으로).
     * gained는 이긴 사람 유행도가 늘어난 양, expiresAt은 이긴 사람 보관함에서 사라지는 시각.
     */
    record StolenKeyword(String text, long score, String result, long gained, Instant expiresAt) {}

    /** 충돌로 빼앗기(4.3): 진 사람이 잃은 키워드를 이긴 사람이 가져간다. 진 사람에게 잃을 키워드가 없으면 비어 있다. */
    Optional<StolenKeyword> stealRandom(long loserId, long winnerId);

    /** 관리자가 넣은 키워드(13.7). externalUntil이 지나면 반영 소스가 internal로 내려간다. created는 이번에 새로 만든 키워드인지. */
    record AdminKeyword(long id, String text, String state, long trendValue, long holderCount, Instant externalUntil, boolean created) {}

    /** 관리자가 그 나라에 🔥활성 키워드를 바로 넣는다: 반영 소스 admin, 지금부터 유지 기간(기본 7일). 이미 있으면 admin으로 바꾸고 다시 센다. */
    AdminKeyword adminAdd(String countryCode, String text);

    /** 관리자 키워드 목록의 한 줄(13.6). */
    record AdminKeywordRow(long id, String text, String normalized, String state, String source, long trendValue, long holderCount,
                           long addCount, long searchCount, long viewCount, Instant externalUntil, Instant activatedAt,
                           Instant bannedAt, Instant createdAt) {}

    /**
     * 키워드 한 개 조회(13.6): 입력한 단어가 등록 규칙(4.6)에 맞는지와, 이미 등록돼 있으면 그 행. 등록 전이라도 관리자가
     * 바로 추가·금지할 수 있게 화면이 이 결과로 카드를 그린다. valid=false면 reason에 이유, keyword=null이면 등록 전이다.
     */
    record AdminKeywordLookup(String text, String normalized, boolean valid, String reason, AdminKeywordRow keyword) {}

    AdminKeywordLookup adminLookup(String countryCode, String text);

    /**
     * 글자로 바로 금지(13.1): 이미 있으면 금지하고, 아직 등록되지 않은 단어면 금지 상태로 새로 등록한다.
     * 누군가 `+`하거나 검색해도 열리지 않고 공개 페이지(4.28)도 404다. 금지를 풀면 💤비활성이다.
     */
    AdminKeywordRow adminBanText(String countryCode, String text);

    /** stateCounts: 상태 필터를 빼고(소스·검색어는 적용) 센 상태별 개수. 필터 칩에 보여 준다. */
    record AdminKeywordPage(List<AdminKeywordRow> items, long total, int page, int size, java.util.Map<String, Long> stateCounts) {}

    /**
     * 관리자 키워드 목록(13.6). state: 활성/비활성/금지(이름, 비우면 전체), source: data_sources.name, q: 검색어,
     * sort: trendValue / holderCount / addCount / searchCount / createdAt / activatedAt / externalUntil, dir: asc·desc.
     */
    /** state·source는 쉼표로 여러 개(예: "활성,금지"), 비우면 전체 */
    AdminKeywordPage adminSearch(String countryCode, String state, String source, String q, String sort, String dir, int page, int size);

    /** 관리자 금지·해제(13.1). 금지하면 트렌드 값이 0이 되어 보유자 점수에서 바로 빠지고, 해제하면 💤비활성이다. */
    AdminKeywordRow adminBan(long keywordId, boolean banned);

    /**
     * 1분 점검(4.8): 나라별 활동 인원을 다시 세고, 🔥 키워드 중 지금 식으로 구한 트렌드 값이 저장값과 다른 것만 고친다.
     * 활동 인원 변화와 관리자 설정 변경(13.3)이 여기서 반영된다. 고친 키워드 수를 돌려준다.
     */
    int refreshTrendValues();

    /** 트렌드 값이 바뀐 키워드를 비우면서 돌려준다. 1분 잡이 보유자 점수를 다시 합산한다(9.4). */
    Set<Long> drainDirtyKeywords();

    List<Long> holderPlayerIds(long keywordId);

    /** 목록형 외부 소스의 제목들을 키워드로 넣는다: 🔥, 반영 소스는 그 소스, 지금부터 유지 기간(기본 7일)(10.2). 새로 유지를 시작한 수. */
    int applyExternalTitles(String countryCode, String sourceName, List<String> titles);

    /**
     * 유지 기간이 끝난 그 소스 키워드를 정리한다. 지금 목록(정규화 키)에 아직 있으면 지금부터 다시 유지 기간을 주고, 없으면 반영 소스를
     * internal로 내린 뒤 가진 사람이 활성 조건(3명) 미만이면 💤로 바꾼다. 내린 수를 돌려준다.
     */
    int expireExternal(String sourceName, Set<String> currentNormalized);

    /** 위와 같되 그 나라의 키워드만(나라별 목록을 쓰는 소스용: 다른 나라 키워드가 엉뚱하게 내려가지 않게). */
    int expireExternal(String countryCode, String sourceName, Set<String> currentNormalized);

    /**
     * 구슬 보상(4.20): 설정 확률(기본 33%)로 그 나라 🔥 키워드 중 완전 무작위 하나를 보관함에 넣고, 나머지는 꽝이다.
     * 이미 가진 키워드가 나오면 남은 시간만 지금부터 다시 24시간으로 갱신한다(RENEWED, 점수·EXP 그대로).
     */
    KeywordDtos.OrbResult openOrb(long playerId);

    /** ! 구슬을 먹었다(4.20). 꽝 없이 항상 🔥 키워드를 얻는다(가능하면 내 보관함에 없는 것). */
    KeywordDtos.OrbResult openPrizeOrb(long playerId);

    /** 보스 처치 보상 키워드 하나(4.35): ! 구슬과 같은 규칙으로 🔥 키워드를 얻지만 구슬을 먹은 횟수(orb_count)와 구슬 통계에는 세지 않는다. */
    KeywordDtos.OrbResult grantRewardKeyword(long playerId);

    /**
     * 봇을 이긴 보상(4.21): 그 나라 🔥 키워드 중 하나를 무작위로 얻는다. 없던 키워드는 일반 추가와 같이 넣고(EXP +1, 24시간),
     * 이미 가진 키워드면 사람에게서 빼앗을 때(4.3)처럼 남은 시간만 지금부터 다시 24시간으로 늘린다(점수·EXP 그대로).
     * 결과 형식은 구슬과 같다. status: GAINED / RENEWED / NONE(🔥 키워드가 없음).
     */
    KeywordDtos.OrbResult gainFromBot(long playerId);

    /** 봇을 이겼다(4.21): 봇이 잃은 그 키워드(keywordId)를 얻는다. 이미 가졌으면 남은 시간만 갱신하고, 그 키워드가 지금 🔥가 아니면 무작위 🔥 키워드로 대신한다. */
    KeywordDtos.OrbResult gainFromBot(long playerId, long keywordId);

    /** 그 나라의 🔥 키워드 목록(방이 봇의 키워드를 고르고 점수를 계산하는 데 쓴다, 4.21). */
    List<BotKeyword> botCatalog(long countryId);
}
