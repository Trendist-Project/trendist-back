package com.trendist.ranking.service;

import com.trendist.ranking.dto.RankingEntry;
import java.util.List;

/** 나라별 현재 유행도 랭킹(4.14). 키워드 랭킹은 두지 않는다. */
public interface RankingService {
    /** TOP 10. 접속 중인 플레이어는 방 메모리 값으로 실시간 보정한다. */
    List<RankingEntry> top(String countryCode);
}
