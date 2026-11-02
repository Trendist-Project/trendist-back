package com.trendist.share.entity;

import com.trendist.player.entity.Player;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자랑하기 카드 공유 주소(4.24). 공유한 순간의 카드 값만 저장하고 이미지는 요청 때 그린다.
 * 카드 값(data)은 고치지 않고, 조회·클릭 수만 늘어난다. 영구 보관한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "share_cards", uniqueConstraints = {
        @UniqueConstraint(name = "uk_share_cards_code", columnNames = "code"),
        @UniqueConstraint(name = "uk_share_cards_player_hash", columnNames = {"player_id", "content_hash"})})
public class ShareCard {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "player_id", nullable = false) private Player player;
    @Column(nullable = false, length = 16) private String code;
    @Column(name = "content_hash", nullable = false, columnDefinition = "CHAR(64)") private String contentHash;
    @Column(nullable = false, columnDefinition = "JSON") private String data;
    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)") private Instant createdAt;
    /** 조회·클릭 수(4.24). 카운터가 1분마다 UPDATE 한 번으로 더하므로 엔티티로는 바꾸지 않는다 */
    @Column(name = "view_count", nullable = false, columnDefinition = "BIGINT DEFAULT 0") private long viewCount;
    @Column(name = "play_count", nullable = false, columnDefinition = "BIGINT DEFAULT 0") private long playCount;
    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)") private Instant updatedAt;

    public ShareCard(Player player, String code, String contentHash, String data, Instant createdAt) {
        this.player = player; this.code = code; this.contentHash = contentHash; this.data = data; this.createdAt = createdAt; this.updatedAt = createdAt;
    }
}
