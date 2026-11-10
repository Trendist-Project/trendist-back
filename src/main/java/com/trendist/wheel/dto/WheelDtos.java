package com.trendist.wheel.dto;

import java.util.List;

public final class WheelDtos {
    private WheelDtos() {}

    /** 돌림판 상태: slices는 칸에 쓰인 숫자(시계 방향, 0번이 맨 위), todayReward는 오늘 이미 돌렸다면 받은 개수, nextAt은 다음에 돌릴 수 있는 시각(epoch ms), locked는 아직 해금 레벨(requiredLevel)이 안 됨 */
    public record Status(boolean enabled, boolean available, boolean locked, int requiredLevel, List<Integer> slices, Integer todayReward, long nextAt) {}

    /** 돌린 결과: index는 멈춘 칸 번호, reward는 받은 활성 키워드 개수, keywords는 실제로 얻은(또는 남은 시간이 갱신된) 키워드 이름 */
    public record SpinResult(int index, int reward, List<String> keywords, Status status) {}
}
