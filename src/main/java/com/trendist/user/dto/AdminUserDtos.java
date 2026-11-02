package com.trendist.user.dto;

import com.trendist.keyword.dto.KeywordDtos;
import com.trendist.profile.dto.ProfileDtos;
import java.time.Instant;
import java.util.List;

/** 관리자 사용자 관리(13.10). 사용자 = 계정, 플레이어 = 계정 × 나라(지금은 KR). */
public final class AdminUserDtos {
    private AdminUserDtos() {}

    /** 목록의 한 줄. type: GUEST / GOOGLE, commentBannedUntil은 댓글 작성 정지 끝(없으면 null) */
    public record Row(long userId, long playerId, String nickname, String type, boolean admin, boolean withdrawn,
                      Instant bannedUntil, Instant uploadBannedUntil, Instant commentBannedUntil,
                      long level, long score, long holdCount, long totalPlusCount, long reportCount,
                      Instant createdAt, Instant lastSeenAt, boolean online, Instant chatBannedUntil) {}

    public record Page(List<Row> items, long total, int page, int size) {}

    /** 플레이어 누적 수치 */
    public record Stats(long exp, long level, long score, long holdCount, long totalPlusCount, long searchCount, long commentScore,
                        long stealCount, long botWinCount, long orbCount, long loseCount, long activeDays,
                        long titleCount, String title, long commentCount, long chatCount) {}

    public record CommentLine(long id, String keyword, String body, long likes, long dislikes, boolean deleted, Instant createdAt) {}

    public record ChatLine(long id, String body, Instant createdAt) {}

    public record ReportLine(long id, String type, String reason, String status, String reporter, String content, Instant createdAt) {}

    public record SanctionLine(long id, String type, String reason, Instant createdAt, Instant expiresAt, Instant releasedAt, boolean active) {}

    /** 사용자 상세: 목록 줄 + 계정 시각, 수치, 꾸미기(장착 모습과 그림), 보관함, 최근 댓글·채팅, 받은 신고, 제재 이력 */
    public record Detail(Row user, Instant lastLoginAt, Instant deletedAt, Instant nicknameChangedAt, Instant lastPlayedAt,
                         Stats stats, ProfileDtos.Look look, ProfileDtos.Art art, List<KeywordDtos.MyKeyword> keywords,
                         List<CommentLine> comments, List<ChatLine> chats, List<ReportLine> reports, List<SanctionLine> sanctions) {}

    /** type: BAN(계정 금지) / UPLOAD(그림·이미지 저장 정지) / COMMENT(댓글 작성 정지) / CHAT(채팅 정지). days가 null이면 영구 */
    public record SanctionRequest(String type, Integer days, String reason) {}

    public record NicknameRequest(String nickname) {}

    public record RoleRequest(boolean admin) {}
}
