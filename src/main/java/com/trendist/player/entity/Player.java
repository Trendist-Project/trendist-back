package com.trendist.player.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.country.entity.Country;
import com.trendist.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 플레이어 = 사용자 × 나라. current_score는 실시간 점수의 DB 값(4.8). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "players",
        uniqueConstraints = @UniqueConstraint(name = "uk_players", columnNames = {"user_id", "country_id"}),
        indexes = @Index(name = "idx_players_ranking", columnList = "country_id, current_score"))
public class Player extends BaseTimeEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "country_id", nullable = false) private Country country;
    @Column(name = "title_id") private Long titleId;
    @Column(nullable = false, length = 40) private String nickname;
    @Column(name = "nickname_changed_at", columnDefinition = "DATETIME(6)") private Instant nicknameChangedAt;
    @Column(nullable = false) private long exp;
    /** 레벨(levels 참조). 누적 EXP로 레벨표에서 정한 행을 가리킨다(4.9). 숫자는 getLevel()로 읽는다 */
    @ManyToOne(optional = false)
    @JoinColumn(name = "level_id", nullable = false, foreignKey = @ForeignKey(name = "fk_players_level"))
    private Level level;
    @Column(name = "current_score", nullable = false) private long currentScore; // 정수 점수(4.8)
    @Column(name = "hold_count", nullable = false) private long holdCount;
    @Column(name = "total_plus_count", nullable = false) private long totalPlusCount;
    @Column(name = "comment_score", nullable = false) private long commentScore;
    /** 누적 검색 횟수(4.4). 검색할 때마다 +1. 줄지 않는다. */
    @Column(name = "search_count", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0") private long searchCount;
    /** 마지막으로 방에 접속해 있던 시각(1분 단위로 고친다). 관리자 통계의 7일·30일 활동 인원과 사용자 관리에 쓴다(13.9, 13.10) */
    @Column(name = "last_seen_at", columnDefinition = "DATETIME(6)") private Instant lastSeenAt;
    /** 마지막으로 키워드를 얻은 시각(처음 만든 시각 포함). 활동한 날을 셀 때 하루에 한 번만 세는 기준이다 */
    @Column(name = "last_played_at", columnDefinition = "DATETIME(6)") private Instant lastPlayedAt;
    // 칭호용 누적 수치(4.10). 모두 줄지 않는다. 컬럼을 추가할 때 기존 행에도 0이 들어가게 DEFAULT를 둔다
    /** 다른 플레이어에게서 키워드를 빼앗은 횟수(4.3) */
    @Column(name = "steal_count", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0") private long stealCount;
    /** 봇을 이겨 보상을 받은 횟수(4.21) */
    @Column(name = "bot_win_count", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0") private long botWinCount;
    /** ? 구슬을 먹은 횟수(꽝 포함, 4.20) */
    @Column(name = "orb_count", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0") private long orbCount;
    /** 충돌로 키워드를 잃은 횟수(4.3) */
    @Column(name = "lose_count", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0") private long loseCount;
    /** 키워드를 얻은 날의 수(나라 시간대 기준으로 서로 다른 날, 처음 만든 날 포함) */
    @Column(name = "active_days", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0") private long activeDays;

    public Player(User user, Country country, String nickname, Level level) {
        this.user = user; this.country = country; this.nickname = nickname; this.level = level;
    }

    /** 레벨 숫자(1~100) */
    public long getLevel() { return level.getLevel(); }
    /** 레벨표 행 */
    public Level getLevelRow() { return level; }

    public void addExp(long amount) { this.exp += amount; }
    public void setLevel(Level level) { this.level = level; }
    public void setCurrentScore(long score) { this.currentScore = score; }
    public void incTotalPlus() { this.totalPlusCount++; }
    public void incHold() { this.holdCount++; }
    public void decHold() { this.holdCount = Math.max(0, holdCount - 1); }
    public void setHoldCount(long holdCount) { this.holdCount = Math.max(0, holdCount); }
    /** 플레이했다(만들기·키워드 얻기). 직전 플레이와 그 나라 시간대로 다른 날이면 활동한 날 +1(4.10) */
    public void touchPlayed(Instant now, ZoneId zone) {
        if (lastPlayedAt == null || !LocalDate.ofInstant(lastPlayedAt, zone).equals(LocalDate.ofInstant(now, zone))) activeDays++;
        this.lastPlayedAt = now;
    }
    public void incSteal() { this.stealCount++; }
    public void incBotWin() { this.botWinCount++; }
    public void incOrb() { this.orbCount++; }
    public void incLose() { this.loseCount++; }
    /** 활성 칭호(4.10). null이면 숨김. 가진 칭호인지는 서비스가 확인한다. */
    public void changeTitle(Long titleId) { this.titleId = titleId; }

    /** 관리자가 바꾼 닉네임(13.10). 사용자의 변경 간격에는 넣지 않는다. */
    public void adminRename(String nickname) { this.nickname = nickname; }

    /** 닉네임 변경. 변경 간격 검사는 서비스가 하고, 여기서는 변경 시각을 함께 남긴다(4.1). */
    public void changeNickname(String nickname, Instant now) {
        this.nickname = nickname;
        this.nicknameChangedAt = now;
    }
}
