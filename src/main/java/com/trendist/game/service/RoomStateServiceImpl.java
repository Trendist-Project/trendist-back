package com.trendist.game.service;

import com.trendist.game.entity.PlayerPosition;
import com.trendist.game.entity.RoomState;
import com.trendist.game.repository.PlayerPositionRepository;
import com.trendist.game.repository.RoomStateRepository;
import com.trendist.game.room.Room;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomStateServiceImpl implements RoomStateService {
    private static final Duration KEEP_ROWS = Duration.ofDays(1);

    private final PlayerPositionRepository positions;
    private final RoomStateRepository states;
    private final Clock clock;

    @Override
    @Transactional
    public void save(long countryId, double area, List<Room.SavedPos> list) {
        Instant now = Instant.now(clock);
        if (!list.isEmpty()) {
            Map<Long, PlayerPosition> rows = new HashMap<>();
            positions.findByPlayerIdIn(list.stream().map(Room.SavedPos::playerId).toList()).forEach(r -> rows.put(r.getPlayerId(), r));
            for (Room.SavedPos p : list) {
                Instant at = now.minusMillis((long) (p.ageSeconds() * 1000));
                PlayerPosition row = rows.get(p.playerId());
                if (row == null) positions.save(new PlayerPosition(p.playerId(), p.x(), p.y(), at));
                else row.move(p.x(), p.y(), at);
            }
        }
        RoomState state = states.findByCountryId(countryId).orElse(null);
        if (state == null) states.save(new RoomState(countryId, area, now)); else state.update(area, now);
        positions.deleteOlderThan(now.minus(KEEP_ROWS));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Room.Resume> position(long playerId) {
        Instant now = Instant.now(clock);
        return positions.findByPlayerId(playerId)
                .map(r -> new Room.Resume(r.getX(), r.getY(), Duration.between(r.getSavedAt(), now).toMillis() / 1000.0))
                .filter(r -> r.ageSeconds() < Room.REJOIN_KEEP_SECONDS);
    }

    @Override
    @Transactional(readOnly = true)
    public OptionalDouble area(long countryId) {
        Instant now = Instant.now(clock);
        return states.findByCountryId(countryId)
                .filter(s -> Duration.between(s.getSavedAt(), now).toMillis() / 1000.0 < Room.REJOIN_KEEP_SECONDS)
                .map(s -> OptionalDouble.of(s.getActiveArea())).orElse(OptionalDouble.empty());
    }
}
