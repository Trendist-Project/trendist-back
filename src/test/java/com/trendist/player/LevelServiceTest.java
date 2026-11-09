package com.trendist.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.trendist.player.entity.Level;
import com.trendist.player.repository.LevelRepository;
import com.trendist.player.service.LevelService;
import com.trendist.player.service.LevelServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 레벨 EXP는 누적하지 않는다(4.9): 레벨이 오를 때마다 그 레벨에 필요한 만큼 빼고 남은 EXP를 이어 가진다. */
class LevelServiceTest {
    /** 1레벨 0, 2레벨 1, 3레벨 5, 4레벨 5(최고 레벨) — 값은 "그 레벨이 되는 데 필요한 EXP" */
    private static LevelService service() {
        LevelRepository repo = mock(LevelRepository.class);
        when(repo.findAllByOrderByLevelAsc()).thenReturn(List.of(new Level(1, 0), new Level(2, 1), new Level(3, 5), new Level(4, 5)));
        return new LevelServiceImpl(repo);
    }

    @Test
    void levelUpSpendsTheRequiredExpAndKeepsTheRest() {
        LevelService s = service();
        var p = s.advance(1, 2); // 2레벨에 1이 필요한데 2를 얻었다: 레벨업하고 1이 남는다
        assertThat(p.row().getLevel()).isEqualTo(2);
        assertThat(p.exp()).isEqualTo(1);
    }

    @Test
    void notEnoughExpKeepsTheLevel() {
        var p = service().advance(2, 4); // 3레벨에는 5가 필요하다
        assertThat(p.row().getLevel()).isEqualTo(2);
        assertThat(p.exp()).isEqualTo(4);
    }

    @Test
    void bigGainRisesOneLevelAtATimeCarryingTheRemainder() {
        var p = service().advance(1, 7); // 1 → 2레벨(−1), 2 → 3레벨(−5), 남은 1
        assertThat(p.row().getLevel()).isEqualTo(3);
        assertThat(p.exp()).isEqualTo(1);
    }

    @Test
    void topLevelOnlyAccumulatesExp() {
        var p = service().advance(4, 100);
        assertThat(p.row().getLevel()).isEqualTo(4);
        assertThat(p.exp()).isEqualTo(100);
    }

    @Test
    void cumulativeAndTotalExpForBotsAndScoreCap() {
        LevelService s = service();
        assertThat(s.cumulative(3)).isEqualTo(6);       // 0 + 1 + 5
        assertThat(s.totalExp(3, 2)).isEqualTo(8);
        assertThat(s.levelFor(6)).isEqualTo(3);         // 누적 6이면 3레벨
        assertThat(s.levelFor(5)).isEqualTo(2);
        assertThat(s.nextRequired(2)).isEqualTo(5);     // 3레벨로 올라가는 데 필요한 양
        assertThat(s.nextRequired(4)).isEqualTo(-1);    // 최고 레벨
    }
}
