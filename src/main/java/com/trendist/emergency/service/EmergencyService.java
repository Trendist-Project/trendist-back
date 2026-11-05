package com.trendist.emergency.service;

import com.trendist.emergency.dto.EmergencyDtos;
import java.util.List;
import java.util.Optional;

/** 긴급 안내(4.29): 관리자가 올리면 모든 사용자의 게임 화면 맨 위에 정해진 시간 동안 흘러간다. */
public interface EmergencyService {
    int MAX_LENGTH = 200;
    int MAX_MINUTES = 720;

    /** 지금 보여 줄 안내(없으면 비어 있다). */
    Optional<EmergencyDtos.Active> active();

    /** 새 안내를 올린다. 켜져 있던 것은 취소되고, 접속 중인 모두에게 바로 알린다. */
    EmergencyDtos.Item create(long authorUserId, EmergencyDtos.Request req);

    /** 관리자가 지금 끝낸다(이미 끝났거나 취소됐으면 그대로). */
    EmergencyDtos.Item cancel(long id);

    /** 관리자 목록(최근 20개, 최신순). */
    List<EmergencyDtos.Item> adminList();
}
