package com.trendist.title.service;

import com.trendist.title.dto.TitleDtos;

/**
 * 칭호(4.10): 조건(METRIC/RANK)을 달성하면 자동으로 주고 알린다. 받은 칭호 중 하나를 활성화하면 닉네임 아래에 보인다.
 * 칭호는 나라별(플레이어별)이다.
 */
public interface TitleService {
    /** 칭호함: 사용할 수 있는 칭호 전체와 보유 여부·획득 시각·진행도. */
    TitleDtos.Box box(long userId);

    /** 활성 칭호를 바꾼다. null이면 칭호를 숨긴다. 가진 칭호만, 5초에 1번. */
    TitleDtos.Box activate(long userId, Long titleId);

    /** 한 플레이어의 METRIC 칭호를 검사해서 새로 달성한 것을 준다. */
    void checkMetrics(long playerId);

    /** 활성 칭호의 이름(없거나 사용 안 함이면 null). 방 입장 때 이름표에 쓴다. */
    String activeTitleName(Long titleId);
}
