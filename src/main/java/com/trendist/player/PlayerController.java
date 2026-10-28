package com.trendist.player;

import com.trendist.auth.AuthUser;
import com.trendist.domain.Country;
import com.trendist.domain.Player;
import com.trendist.game.RoomManager;
import com.trendist.repo.PlayerRepository;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PlayerController {
    private final PlayerService playerService;
    private final PlayerRepository players;
    private final RoomManager rooms;

    public PlayerController(PlayerService playerService, PlayerRepository players, RoomManager rooms) {
        this.playerService = playerService;
        this.players = players;
        this.rooms = rooms;
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public PlayerDtos.Me me(@AuthenticationPrincipal AuthUser user) {
        var p = playerService.findCurrent(user.userId()).map(playerService::summary).orElse(null);
        return new PlayerDtos.Me(user.userId(), true, p);
    }

    /** 닉네임 정하기(첫 입장). */
    @PostMapping("/players")
    @Transactional
    public PlayerDtos.PlayerSummary create(@AuthenticationPrincipal AuthUser user,
                                           @Valid @RequestBody PlayerDtos.CreatePlayerRequest req) {
        return playerService.summary(playerService.create(user.userId(), req.nickname(), req.countryCode()));
    }

    /** 현재 유행도 TOP 10(4.14). 접속 중인 플레이어는 방 메모리 값으로 실시간 보정한다. */
    @GetMapping("/rankings")
    @Transactional(readOnly = true)
    public List<PlayerDtos.RankingEntry> rankings(@RequestParam(defaultValue = "KR") String country) {
        Country c = playerService.country(country);
        Map<Long, Double> live = rooms.liveScores(c.getId());
        record Row(Player p, BigDecimal score) {}
        List<Row> rows = new ArrayList<>();
        for (Player p : players.findRanking(c.getId(), PageRequest.of(0, 50))) {
            Double v = live.get(p.getId());
            rows.add(new Row(p, v != null ? BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP) : p.getCurrentScore()));
        }
        rows.sort(Comparator.comparing(Row::score).reversed());
        List<PlayerDtos.RankingEntry> out = new ArrayList<>();
        for (int i = 0; i < Math.min(10, rows.size()); i++) {
            Row r = rows.get(i);
            out.add(new PlayerDtos.RankingEntry(i + 1, r.p().getId(), r.p().getNickname(), r.p().getLevel(), r.score()));
        }
        return out;
    }
}
