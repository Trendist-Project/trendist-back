package com.trendist.keyword.service;

import com.trendist.keyword.dto.KeywordDtos;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 검색, +, 내 트렌드 키워드, 만료, 충돌로 잃기(4.4~4.8, 9.4). */
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
}
