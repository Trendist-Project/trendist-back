package com.trendist.ranking.service;

import com.trendist.ranking.dto.RankingEntry;
import java.util.List;

/** 나라별 현재 유행도 랭킹(4.14). 지금 그 나라 방에 접속한 사람만 집계한다. 키워드 랭킹은 두지 않는다. */
public interface RankingService {
    /** 내 순위(지금 방에 접속 중일 때만, 아니면 null). 리더보드에 안 보이는 순위도 알려 준다. 정렬은 top()과 같다 */
    RankingEntry mine(long userId);

    /** 접속 중인 플레이어의 TOP 10. 방 메모리의 실시간 점수(1초마다 갱신)로 정한다. */
    List<RankingEntry> top(String countryCode);
}
