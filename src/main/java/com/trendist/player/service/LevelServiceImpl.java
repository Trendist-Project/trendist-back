package com.trendist.player.service;

import com.trendist.player.entity.Level;
import com.trendist.player.repository.LevelRepository;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * levels 표를 메모리에 두고 레벨을 정한다(4.9): 레벨마다 필요한 EXP를 쓰고 남은 EXP를 이어 가진다. 관리자가 바꾸면 1분 안에 반영된다.
 * 메모리의 행은 트랜잭션 밖에서 읽은 것이라 플레이어에 넣을 때는 번호(id)만 쓰인다.
 */
@Service
public class LevelServiceImpl implements LevelService {
    private final LoadingCache<String, List<Level>> cache;

    public LevelServiceImpl(LevelRepository repo) {
        this.cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).build(k -> List.copyOf(repo.findAllByOrderByLevelAsc()));
    }

    @Override
    public int levelFor(long totalExp) {
        if (cache.get("all").isEmpty()) return 1; // 시드가 넣기 전(봇 레벨 등)
        return (int) rowFor(totalExp).getLevel();
    }

    @Override
    public Level rowFor(long totalExp) {
        List<Level> all = cache.get("all");
        if (all.isEmpty()) throw new IllegalStateException("levels 표가 비어 있어요");
        Level found = all.get(0);
        long sum = 0;
        for (Level l : all) {
            sum += l.getRequiredExp();
            if (totalExp >= sum) found = l; else break;
        }
        return found;
    }

    @Override
    public Progress advance(long level, long exp) {
        List<Level> all = cache.get("all");
        if (all.isEmpty()) throw new IllegalStateException("levels 표가 비어 있어요");
        int i = (int) Math.max(1, Math.min(level, all.size())) - 1;
        while (i + 1 < all.size() && exp >= all.get(i + 1).getRequiredExp()) { // 다음 레벨에 필요한 만큼 쓰고 남은 EXP를 이어 갖는다
            exp -= all.get(i + 1).getRequiredExp();
            i++;
        }
        return new Progress(all.get(i), exp);
    }

    @Override
    public long cumulative(long level) {
        long sum = 0;
        for (Level l : cache.get("all")) { if (l.getLevel() > level) break; sum += l.getRequiredExp(); }
        return sum;
    }

    @Override
    public Level row(long level) {
        for (Level l : cache.get("all")) if (l.getLevel() == level) return l;
        throw new IllegalArgumentException("levels 표에 " + level + "레벨이 없어요");
    }

    @Override
    public List<Level> all() { return cache.get("all"); }

    @Override
    public List<Badge> badges() {
        return cache.get("all").stream()
                .map(l -> new Badge((int) l.getLevel(), l.getBadgeColor(), l.getBadgeAlpha() == null ? 100 : l.getBadgeAlpha().intValue(), l.isBadgeRainbow()))
                .toList();
    }

    /** 다음 레벨로 올라가는 데 필요한 EXP. 최고 레벨이면 -1. */
    @Override
    public long nextRequired(long level) {
        List<Level> all = cache.get("all");
        return level < all.size() ? all.get((int) level).getRequiredExp() : -1;
    }
}
