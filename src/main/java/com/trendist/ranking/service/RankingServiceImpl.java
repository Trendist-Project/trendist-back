package com.trendist.ranking.service;

import com.trendist.country.entity.Country;
import com.trendist.country.service.CountryService;
import com.trendist.game.room.Room;
import com.trendist.game.service.RoomManager;
import com.trendist.ranking.dto.RankingEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RankingServiceImpl implements RankingService {
    /** 서버는 항상 TOP 10을 준다. 몇 위까지 볼지(1·3·10)는 사람마다 화면에서 고른다 */
    private static final int TOP = 10;

    private final CountryService countries;
    private final RoomManager rooms;
    private final com.trendist.player.service.PlayerService players;

    @Override
    public RankingEntry mine(long userId) {
        com.trendist.player.entity.Player me = players.requireCurrent(userId);
        List<Room.Live> live = rooms.livePlayers(me.getCountry().getId()).stream()
                .sorted(Comparator.comparingDouble(Room.Live::score).reversed().thenComparingLong(Room.Live::playerId))
                .toList();
        for (int i = 0; i < live.size(); i++) {
            Room.Live l = live.get(i);
            if (l.playerId() == me.getId()) return new RankingEntry(i + 1, l.playerId(), l.nickname(), l.level(), Math.round(l.score()));
        }
        return null;
    }

    @Override
    public List<RankingEntry> top(String countryCode) {
        Country c = countries.requireOpen(countryCode);
        // 지금 그 나라 방에 접속한 사람만 겨룬다(4.14). DB는 보지 않고 방 메모리의 실시간 점수로 정렬한다
        List<Room.Live> live = rooms.livePlayers(c.getId()).stream()
                .sorted(Comparator.comparingDouble(Room.Live::score).reversed().thenComparingLong(Room.Live::playerId))
                .limit(TOP)
                .toList();
        List<RankingEntry> out = new ArrayList<>();
        for (int i = 0; i < live.size(); i++) {
            Room.Live l = live.get(i);
            out.add(new RankingEntry(i + 1, l.playerId(), l.nickname(), l.level(),
                    Math.round(l.score())));
        }
        return out;
    }
}
