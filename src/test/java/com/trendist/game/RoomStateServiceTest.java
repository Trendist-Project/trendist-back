package com.trendist.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.trendist.game.room.Room;
import com.trendist.game.service.RoomStateService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 위치·활동 영역 저장(9.2): 서버를 다시 켠 뒤 5분 안이면 이어 가고, 지났으면 쓰지 않는다. */
@SpringBootTest
@ActiveProfiles("test")
class RoomStateServiceTest {
    @Autowired RoomStateService states;
    @Autowired JdbcTemplate jdbc;

    @Test
    void savesAndRestoresPositionAndAreaWithinFiveMinutes() {
        states.save(777, 9000, List.of(new Room.SavedPos(90001, 12.5, -34.5, 0), new Room.SavedPos(90002, 1, 2, 120)));
        var live = states.position(90001).orElseThrow();
        assertThat(live.x()).isEqualTo(12.5);
        assertThat(live.y()).isEqualTo(-34.5);
        assertThat(live.ageSeconds()).isBetween(0.0, 5.0);
        assertThat(states.position(90002).orElseThrow().ageSeconds()).isBetween(119.0, 126.0); // 나간 지 2분 전 위치로 저장
        assertThat(states.area(777).getAsDouble()).isEqualTo(9000);
        assertThat(states.position(99999)).isEmpty();
        assertThat(states.area(778)).isEmpty();
    }

    @Test
    void positionOlderThanFiveMinutesIsNotUsedAndSaveOverwrites() {
        states.save(779, 8000, List.of(new Room.SavedPos(90003, 5, 5, 301)));      // 이미 5분이 지난 위치
        assertThat(states.position(90003)).isEmpty();
        states.save(779, 7000, List.of(new Room.SavedPos(90003, 6, 7, 0)));        // 같은 사람은 덮어쓴다
        assertThat(states.position(90003).orElseThrow().x()).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from player_positions where player_id = 90003", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from room_states where country_id = 779", Integer.class)).isEqualTo(1);
        assertThat(states.area(779).getAsDouble()).isEqualTo(7000);
        jdbc.update("update room_states set saved_at = timestampadd(minute, -6, current_timestamp) where country_id = 779");
        assertThat(states.area(779)).isEmpty();                                    // 6분 전 영역은 쓰지 않는다
    }
}
