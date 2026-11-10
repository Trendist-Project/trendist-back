package com.trendist.wheel.service;

import com.trendist.common.exception.ApiException;
import com.trendist.common.support.DailyReset;
import com.trendist.keyword.dto.KeywordDtos;
import com.trendist.keyword.service.KeywordService;
import com.trendist.player.entity.Player;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.player.service.PlayerService;
import com.trendist.setting.service.SettingService;
import com.trendist.setting.support.SettingKeys;
import com.trendist.wheel.dto.WheelDtos;
import com.trendist.wheel.entity.WheelSpin;
import com.trendist.wheel.repository.WheelSpinRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 돌림판: "하루"는 플레이어 나라 시간대 기준이고 초기화 시각은 설정(daily_reset_hour·minute, 기본 00:00)이다. 칸은 SLICES(1~20)이고 칸마다 설정의 비중(wheel_weight_N)으로 뽑는다.
 * 비중이 모두 0이면 가장 작은 칸이다. 뽑힌 개수만큼 구슬 횟수는 세지 않는 🔥 키워드를 지급한다(보스 보상과 같은 규칙).
 */
@Service
@RequiredArgsConstructor
public class WheelServiceImpl implements WheelService {
    private final PlayerService playerService;
    private final PlayerRepository players;
    private final WheelSpinRepository spins;
    private final KeywordService keywordService;
    private final SettingService settings;
    private final DailyReset daily;

    @Override
    @Transactional(readOnly = true)
    public WheelDtos.Status status(long userId) {
        Player p = playerService.requireCurrent(userId);
        ZoneId zone = ZoneId.of(p.getCountry().getTimezone());
        LocalDate today = daily.today(zone);
        return statusOf(p, zone, today, spins.findByPlayerIdAndSpinDate(p.getId(), today).orElse(null));
    }

    @Override
    @Transactional
    public WheelDtos.SpinResult spin(long userId) {
        if (settings.getInt(SettingKeys.WHEEL_ENABLED) <= 0) throw new ApiException(HttpStatus.FORBIDDEN, "WHEEL_OFF", "돌림판을 쓸 수 없어요");
        Player cur = playerService.requireCurrent(userId);
        Player p = players.findByIdForUpdate(cur.getId()).orElseThrow(); // 같은 사람이 동시에 두 번 누르지 못하게 행을 잠근다
        if (p.getLevel() < settings.getInt(SettingKeys.WHEEL_UNLOCK_LEVEL)) throw new ApiException(HttpStatus.FORBIDDEN, "WHEEL_LEVEL", "레벨이 부족해요"); // 해금 레벨
        ZoneId zone = ZoneId.of(p.getCountry().getTimezone());
        LocalDate today = daily.today(zone);
        if (spins.findByPlayerIdAndSpinDate(p.getId(), today).isPresent()) throw new ApiException(HttpStatus.CONFLICT, "WHEEL_USED", "오늘은 이미 돌렸어요");
        int index = pick();
        int reward = SLICES[index];
        WheelSpin spin = spins.save(new WheelSpin(p.getId(), today, reward));
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < reward; i++) {
            KeywordDtos.OrbResult r = keywordService.grantRewardKeyword(p.getId());
            if (("GAINED".equals(r.status()) || "RENEWED".equals(r.status())) && r.text() != null) texts.add(r.text());
        }
        return new WheelDtos.SpinResult(index, reward, texts, statusOf(p, zone, today, spin));
    }

    private WheelDtos.Status statusOf(Player p, ZoneId zone, LocalDate today, WheelSpin spin) {
        boolean enabled = settings.getInt(SettingKeys.WHEEL_ENABLED) > 0;
        int need = settings.getInt(SettingKeys.WHEEL_UNLOCK_LEVEL);
        boolean locked = p.getLevel() < need;
        long nextAt = daily.nextReset(zone).toEpochMilli();
        List<Integer> slices = new ArrayList<>();
        for (int s : SLICES) slices.add(s);
        return new WheelDtos.Status(enabled, enabled && !locked && spin == null, locked, need, slices, spin == null ? null : spin.getRewardCount(), nextAt);
    }

    /** 설정의 비중(wheel_weight_N)으로 칸 하나를 뽑는다. 음수는 0으로 본다. 비중이 모두 0이면 첫 칸 */
    private int pick() {
        double[] w = new double[SLICES.length];
        double total = 0;
        for (int i = 0; i < SLICES.length; i++) { w[i] = Math.max(0, settings.getDouble("wheel_weight_" + SLICES[i])); total += w[i]; }
        if (total <= 0) return 0;
        return choose(w, ThreadLocalRandom.current().nextDouble(total));
    }

    /** 0 이상 합 미만의 roll이 어느 칸 구간에 드는지(비중 w를 차례로 이어 붙인 구간). 비중이 0인 칸은 절대 뽑히지 않는다 */
    static int choose(double[] w, double roll) {
        for (int i = 0; i < w.length; i++) { roll -= w[i]; if (roll < 0) return i; }
        for (int i = w.length - 1; i >= 0; i--) if (w[i] > 0) return i; // 부동소수 오차 대비: 마지막으로 비중이 있는 칸
        return 0;
    }
}
