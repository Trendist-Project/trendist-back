package com.trendist.wheel.service;

import com.trendist.wheel.dto.WheelDtos;

/** 하루에 한 번 돌리는 돌림판(활성 키워드 지급). */
public interface WheelService {
    /** 칸에 쓰인 숫자(시계 방향). 최대 20. 칸마다 설정 `wheel_weight_<숫자>`가 뽑힐 비중이다 */
    int[] SLICES = {1, 2, 3, 4, 5, 6, 8, 10, 15, 20};

    WheelDtos.Status status(long userId);

    /** 돌린다: 오늘 이미 돌렸으면 409, 꺼져 있으면 403. 뽑힌 개수만큼 🔥 활성 키워드를 바로 지급한다. */
    WheelDtos.SpinResult spin(long userId);
}
