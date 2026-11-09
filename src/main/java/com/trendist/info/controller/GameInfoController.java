package com.trendist.info.controller;

import com.trendist.player.entity.Level;
import com.trendist.player.service.LevelService;
import com.trendist.setting.service.SettingService;
import com.trendist.setting.support.SettingKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게임 안내에 공개하는 운영 값(4.17): 가이드가 숫자를 글에 박아 두면 운영에서 값을 바꿀 때 어긋나므로, 가이드 그림이 이 API로 지금 값을 읽어 보여 준다.
 * 로그인 없이 읽는다. 확률처럼 사용자에게 알려야 하는 값만 둔다.
 */
@RestController
@RequestMapping("/api/game-info")
@RequiredArgsConstructor
public class GameInfoController {
    private final SettingService settings;
    private final LevelService levels;

    /** ? 구슬 확률(4.20): 🔥활성 키워드를 얻을 확률(%)과 나머지(가진 키워드를 잃거나 꽝). ! 구슬은 항상 얻는다 */
    public record OrbOdds(int gainPercent, int losePercent) {}

    @GetMapping("/orb-odds")
    public OrbOdds orbOdds() {
        int gain = Math.max(0, Math.min(100, settings.getInt(SettingKeys.ORB_ACTIVE_PERCENT)));
        return new OrbOdds(gain, 100 - gain);
    }

    public record LevelExp(int level, long requiredExp) {}

    /** 모든 레벨(1~100)의 필요 EXP(앞 레벨에서 올라올 때 모아야 하는 양). 가이드의 "레벨별 필요 경험치" 표가 쓴다(levels 표, 1분 캐시) */
    @GetMapping("/levels")
    public java.util.List<LevelExp> allLevels() {
        return levels.all().stream().map(l -> new LevelExp((int) l.getLevel(), l.getRequiredExp())).toList();
    }

    /** 레벨별로 그 레벨이 되는 데 모아야 하는 EXP 전체(1~그 레벨의 합, 누적). levels=10,15,20 처럼 최대 30개, 1~100만 */
    @GetMapping("/level-exp")
    public Map<Integer, Long> levelExp(@RequestParam("levels") java.util.List<Integer> wanted) {
        Map<Integer, Long> out = new LinkedHashMap<>();
        for (Integer lv : wanted.stream().distinct().limit(30).toList()) {
            if (lv == null || lv < 1 || lv > 100) continue;
            try {
                levels.row(lv); // 표에 있는 레벨만
                out.put(lv, levels.cumulative(lv));
            } catch (RuntimeException e) { /* 표에 없는 레벨은 뺀다 */ }
        }
        return out;
    }
}
