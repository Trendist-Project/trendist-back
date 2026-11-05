package com.trendist.emergency.event;

import com.trendist.emergency.dto.EmergencyDtos;

/** 긴급 안내가 바뀌었다(4.29). 커밋된 뒤 접속 중인 모두에게 게임 서버가 알린다. */
public final class EmergencyEvents {
    private EmergencyEvents() {}

    /** active가 null이면 지금 보여 줄 것이 없다(취소됨) */
    public record Changed(EmergencyDtos.Active active) {}
}
