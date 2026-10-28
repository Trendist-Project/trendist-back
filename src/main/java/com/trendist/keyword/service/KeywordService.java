package com.trendist.keyword.service;

import com.trendist.keyword.dto.KeywordDtos;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 검색, +, 보관함, 만료, 충돌로 잃기(4.4~4.8, 9.4). */
public interface KeywordService {
    KeywordDtos.SearchResult search(long userId, String q);

    KeywordDtos.PlusResult plus(long userId, String rawText);

    List<KeywordDtos.MyKeyword> myKeywords(long userId);

    /** 방 입장 때 보유 색인에 넣을 키워드 번호(만료 전, 금지 아님). */
    List<Long> activeKeywordIds(long playerId);

    /** 만료된 + 하나를 지우고 인원·트렌드 값·점수를 고친다. 지웠으면 true. */
    boolean expireOne(long playerKeywordId);

    /** 충돌로 잃기(4.3): 금지되지 않은 키워드 중 하나를 무작위로 지운다. 잃은 키워드 표시명을 돌려준다. */
    Optional<String> loseRandom(long playerId);

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
