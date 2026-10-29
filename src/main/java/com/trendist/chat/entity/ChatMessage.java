package com.trendist.chat.entity;

import com.trendist.country.entity.Country;
import com.trendist.player.entity.Player;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 채팅 메시지(4.13). 고치지 않는 기록이라 created_at만 둔다(11.1). 30일 뒤 지운다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_messages_room", columnList = "country_id, created_at"),
        @Index(name = "idx_chat_messages_created", columnList = "created_at")})
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "country_id", nullable = false) private Country country;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "player_id", nullable = false) private Player player;
    @Column(nullable = false, length = 100) private String body;
    @Column(name = "report_count", nullable = false) private long reportCount;
    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)") private Instant createdAt;

    public ChatMessage(Country country, Player player, String body, Instant createdAt) {
        this.country = country; this.player = player; this.body = body; this.createdAt = createdAt;
    }
}
