package com.trendist.game.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 방(나라)의 저장 상태(9.2): 지금의 활동 영역 한 변. 서버를 다시 켜면 이 크기에서 시작해 서서히 줄여서, 돌아온 플레이어가
 * 좁아진 영역 가장자리로 밀려나지 않게 한다. 나라당 한 행이고, saved_at이 5분을 넘으면 쓰지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "room_states", uniqueConstraints = @UniqueConstraint(name = "uk_room_states_country", columnNames = "country_id"))
public class RoomState extends BaseTimeEntity {
    @Column(name = "country_id", nullable = false) private long countryId;
    @Column(name = "active_area", nullable = false) private double activeArea;
    @Column(name = "saved_at", nullable = false, columnDefinition = "DATETIME(6)") private Instant savedAt;

    public RoomState(long countryId, double activeArea, Instant savedAt) {
        this.countryId = countryId; this.activeArea = activeArea; this.savedAt = savedAt;
    }

    public void update(double activeArea, Instant savedAt) { this.activeArea = activeArea; this.savedAt = savedAt; }
}
