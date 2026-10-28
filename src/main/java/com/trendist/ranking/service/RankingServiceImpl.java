package com.trendist.ranking.service;

import com.trendist.country.entity.Country;
import com.trendist.country.service.CountryService;
import com.trendist.game.service.RoomManager;
import com.trendist.player.entity.Player;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.ranking.dto.RankingEntry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RankingServiceImpl implements RankingService {
    private static final int TOP = 10;
    private static final int CANDIDATES = 50; // DB 상위 후보. 접속 중인 사람의 실시간 점수로 다시 정렬한다

    private final CountryService countries;
    private final PlayerRepository players;
    private final RoomManager rooms;

    @Override
    @Transactional(readOnly = true)
    public List<RankingEntry> top(String countryCode) {
        Country c = countries.requireOpen(countryCode);
        Map<Long, Double> live = rooms.liveScores(c.getId());
        record Row(Player p, BigDecimal score) {}
        List<Row> rows = new ArrayList<>();
        for (Player p : players.findRanking(c.getId(), PageRequest.of(0, CANDIDATES))) {
            Double v = live.get(p.getId());
            rows.add(new Row(p, v != null ? BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP) : p.getCurrentScore()));
        }
        rows.sort(Comparator.comparing(Row::score).reversed());
        List<RankingEntry> out = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP, rows.size()); i++) {
            Row r = rows.get(i);
            out.add(new RankingEntry(i + 1, r.p().getId(), r.p().getNickname(), r.p().getLevel(), r.score()));
        }
        return out;
    }
}
