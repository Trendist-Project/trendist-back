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

    /** 키워드 상세 카드(4.12). 내 나라 키워드만, 금지 키워드는 볼 수 없다. */
    KeywordDtos.KeywordCard detail(long userId, long keywordId);

    /** 방 입장 때 보유 색인에 넣을 키워드 번호(만료 전, 금지 아님). */
    List<Long> activeKeywordIds(long playerId);

    /** 만료된 + 하나를 지우고 인원·트렌드 값·점수를 고친다. 지웠으면 true. */
    boolean expireOne(long playerKeywordId);

    /** 충돌로 잃은 키워드: 이름과 잃는 순간의 키워드 점수(💤면 0). 이름은 이긴 쪽에만, 점수는 양쪽에 보여 준다(4.3). */
    record LostKeyword(String text, long score) {}

    /** 충돌로 잃기(4.3): 금지되지 않은 키워드 중 하나를 무작위로 지운다. */
    Optional<LostKeyword> loseRandom(long playerId);

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
    record AdminKeywordRow(long id, String text, String state, String source, long trendValue, long holderCount,
                           long totalPlusCount, long searchCount, Instant externalUntil, Instant activatedAt,
                           Instant bannedAt, Instant createdAt) {}

    record AdminKeywordPage(List<AdminKeywordRow> items, long total, int page, int size) {}

    /**
     * 관리자 키워드 목록(13.6). state: 활성/비활성/금지(이름, 비우면 전체), source: data_sources.name, q: 검색어,
     * sort: trendValue / holderCount / totalPlusCount / searchCount / createdAt / activatedAt / externalUntil, dir: asc·desc.
     */
    AdminKeywordPage adminSearch(String countryCode, String state, String source, String q, String sort, String dir, int page, int size);

    /** 관리자 금지·해제(13.1). 금지하면 트렌드 값이 0이 되어 보유자 점수에서 바로 빠지고, 해제하면 💤비활성이다. */
    AdminKeywordRow adminBan(long keywordId, boolean banned);

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

    /**
     * 구슬 보상(4.20): 설정 확률(기본 33%)로 그 나라 🔥 키워드 중 완전 무작위 하나를 보관함에 넣고, 나머지는 꽝이다.
     * 이미 가진 키워드가 나오면 아무것도 하지 않는다.
     */
    KeywordDtos.OrbResult openOrb(long playerId);
}
