package com.trendist.game.service;

import com.trendist.game.room.Room;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** 방 메모리에만 있던 위치·활동 영역을 DB에 저장해 서버를 다시 켜도 이어 가게 한다(9.2). 호출은 틱 스레드 밖에서 한다. */
public interface RoomStateService {
    /** 위치와 활동 영역을 저장한다(플레이어당·나라당 한 행을 덮어쓴다). ageSeconds만큼 전에 그 자리에 있었던 것으로 저장한다. */
    void save(long countryId, double area, List<Room.SavedPos> positions);

    /** 저장해 둔 마지막 위치. 없거나 REJOIN_KEEP_SECONDS를 넘었으면 빈 값. */
    Optional<Room.Resume> position(long playerId);

    /** 저장해 둔 활동 영역. 없거나 REJOIN_KEEP_SECONDS를 넘었으면 빈 값. */
    OptionalDouble area(long countryId);
}
