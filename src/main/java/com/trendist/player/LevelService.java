package com.trendist.player;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.trendist.domain.Level;
import com.trendist.repo.LevelRepository;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

/** levels 표를 메모리에 두고 EXP로 레벨을 정한다(4.9). 관리자가 바꾸면 1분 안에 반영된다. */
@Service
public class LevelService {
    private final LoadingCache<String, long[]> cache;

    public LevelService(LevelRepository repo) {
        this.cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).build(k -> {
            List<Level> all = repo.findAllByOrderByLevelAsc();
            long[] req = new long[all.size()];
            for (int i = 0; i < all.size(); i++) req[i] = all.get(i).getRequiredExp();
            return req;
        });
    }

    public int levelFor(long exp) {
        long[] req = cache.get("all");
        int level = 1;
        for (int i = 0; i < req.length; i++) if (exp >= req[i]) level = i + 1;
        return level;
    }

    /** 다음 레벨에 필요한 누적 EXP. 최고 레벨이면 -1. */
    public long nextRequired(int level) {
        long[] req = cache.get("all");
        return level < req.length ? req[level] : -1;
    }
}
