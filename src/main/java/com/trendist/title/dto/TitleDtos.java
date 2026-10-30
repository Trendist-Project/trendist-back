package com.trendist.title.dto;

import java.time.Instant;
import java.util.List;

/** 칭호함(4.10). */
public final class TitleDtos {
    private TitleDtos() {}

    /**
     * 칭호 하나. owned면 acquiredAt이 있다. 조건 진행: METRIC은 current / target(지금 값 / 기준값),
     * RANK는 target이 "N위 안"이고 current는 쓰지 않는다(0).
     */
    public record Item(long id, String name, String description, String conditionType, String metric,
                       long current, long target, boolean owned, Instant acquiredAt, boolean active) {}

    public record Box(Long activeTitleId, List<Item> titles) {}

    public record ActivateRequest(Long titleId) {}
}
