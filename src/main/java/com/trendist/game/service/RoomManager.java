package com.trendist.game.service;

import com.trendist.game.room.Room;
import java.util.Map;

/**
 * 나라별 방을 만들고 틱을 돌리며, 서비스 이벤트를 방에 전달한다(9.2).
 * 지금은 서버 1대·나라당 방 하나이고, 지역당 여러 방·여러 서버로 늘릴 때 구현만 바꾼다.
 */
public interface RoomManager {
    /** 그 나라의 방. 없으면 만들고 틱을 시작한다. */
    Room room(long countryId);

    /** settings에서 읽은 지금의 방 설정값. */
    Room.Tuning tuning();

    /** 클라이언트 예측에 필요한 값(9.2 설정값 전달). */
    Map<String, Object> tuningMessage(Room.Tuning t);

    /** 접속 중인 플레이어 점수. 접속 중이 아니면 null. */
    Double liveScore(long countryId, long playerId);

    /** 그 나라 방의 접속 중인 플레이어 점수(랭킹 실시간 보정용). */
    Map<Long, Double> liveScores(long countryId);

    void sendText(Room.Member m, Map<String, Object> message);

    /** DB·외부 호출처럼 틱 스레드에서 하면 안 되는 일을 작업 스레드에서 돌린다. */
    void submitWork(Runnable r);
}
