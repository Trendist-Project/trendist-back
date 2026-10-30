package com.trendist.comment.service;

import com.trendist.comment.dto.CommentDtos;
import com.trendist.comment.entity.Comment;
import com.trendist.comment.entity.CommentVote;
import com.trendist.comment.repository.CommentRepository;
import com.trendist.comment.repository.CommentVoteRepository;
import com.trendist.common.code.CodeTables;
import com.trendist.common.exception.ApiException;
import com.trendist.common.support.RateLimiter;
import com.trendist.common.support.TextRules;
import com.trendist.game.event.GameEvents;
import com.trendist.keyword.entity.Keyword;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.player.entity.Player;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.player.service.PlayerService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {
    private static final int TOP = 3;
    private static final int MIN_LENGTH = 1, MAX_LENGTH = 200; // 최소 글자 수 제한은 없다. 빈 댓글만 막는다
    private static final Duration EDIT_WINDOW = Duration.ofMinutes(5);
    private static final String DELETED_TEXT = "삭제된 댓글이에요";
    private static final String WITHDRAWN_TEXT = "탈퇴한 사용자의 댓글이에요";
    private static final String WITHDRAWN_NAME = "탈퇴한 사용자";

    private final CommentRepository comments;
    private final CommentVoteRepository votes;
    private final KeywordRepository keywords;
    private final PlayerRepository players;
    private final PlayerService playerService;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // ---------------------------------------------------------------- 조회

    @Override
    @Transactional(readOnly = true)
    public List<CommentDtos.Item> top(long userId, long keywordId) {
        Player me = playerService.requireCurrent(userId);
        Keyword k = requireKeyword(me, keywordId);
        return items(me, comments.findTop(k.getId(), PageRequest.of(0, TOP)));
    }

    @Override
    @Transactional(readOnly = true)
    public CommentDtos.Page latest(long userId, long keywordId, int page, int size) {
        Player me = playerService.requireCurrent(userId);
        Keyword k = requireKeyword(me, keywordId);
        List<Long> topIds = comments.findTop(k.getId(), PageRequest.of(0, TOP)).stream().map(Comment::getId).toList();
        var result = comments.findLatest(k.getId(), topIds.isEmpty() ? List.of(-1L) : topIds,
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 50)));
        return new CommentDtos.Page(items(me, result.getContent()), result.getNumber(), result.getTotalPages(),
                comments.countByKeywordId(k.getId()));
    }

    // ---------------------------------------------------------------- 쓰기·고치기·지우기

    @Override
    @Transactional
    public CommentDtos.Item write(long userId, long keywordId, String raw) {
        Player me = playerService.requireCurrent(userId);
        Keyword k = requireKeyword(me, keywordId);
        // 작성은 기획상 로그인 사용자만이지만, 구글 로그인을 만들기 전까지는 게스트도 쓸 수 있게 둔다(4.12). 작성 수 한도는 없다
        String body = validBody(raw);
        Comment c = comments.save(new Comment(k, me, body));
        comments.flush();
        players.addCommentScore(me.getId(), 1);
        events.publishEvent(new GameEvents.TitleCheck(me.getId())); // 댓글 점수 칭호(4.10)
        return item(me, c, null);
    }

    @Override
    @Transactional
    public CommentDtos.Item edit(long userId, long commentId, String raw) {
        Player me = playerService.requireCurrent(userId);
        Comment c = requireMine(me, commentId);
        if (!editable(c)) throw ApiException.conflict("COMMENT_EDIT_EXPIRED", "댓글은 쓴 뒤 5분 안에만 고칠 수 있어요");
        c.edit(validBody(raw));
        comments.flush();
        return item(me, c, votes.findByCommentIdAndPlayerId(c.getId(), me.getId()).map(CommentVote::getVote).orElse(null));
    }

    @Override
    @Transactional
    public void delete(long userId, long commentId) {
        Player me = playerService.requireCurrent(userId);
        Comment c = requireMine(me, commentId);
        c.delete(Instant.now(clock));
        comments.flush();
        players.addCommentScore(me.getId(), -1);
        events.publishEvent(new GameEvents.TitleCheck(me.getId())); // 댓글 점수 칭호(4.10)
    }

    // ---------------------------------------------------------------- 투표

    @Override
    @Transactional
    public CommentDtos.VoteResult vote(long userId, long commentId, String vote) {
        if (!CommentVote.LIKE.equals(vote) && !CommentVote.DISLIKE.equals(vote)) {
            throw ApiException.badRequest("VOTE_INVALID", "투표 값이 올바르지 않아요");
        }
        Player me = playerService.requireCurrent(userId);
        // 사용자당 분당 20회, 같은 댓글 분당 5회(9.5)
        if (!rateLimiter.tryAcquire("vote:" + me.getId(), 20, Duration.ofMinutes(1))
                || !rateLimiter.tryAcquire("vote:comment:" + me.getId() + ":" + commentId, 5, Duration.ofMinutes(1))) {
            throw ApiException.tooMany("VOTE_TOO_FAST", "투표를 너무 자주 하고 있어요. 잠시 후 다시 해 주세요");
        }
        Comment c = comments.findById(commentId)
                .filter(x -> sameCountry(me, x.getKeyword()))
                .orElseThrow(() -> ApiException.notFound("COMMENT_NOT_FOUND", "댓글을 찾을 수 없어요"));
        if (c.getPlayer().getId().equals(me.getId())) throw ApiException.badRequest("VOTE_OWN", "내 댓글에는 투표할 수 없어요");
        if (c.isDeleted() || withdrawn(c.getPlayer())) throw ApiException.conflict("VOTE_GONE", "삭제된 댓글에는 투표할 수 없어요");

        Optional<CommentVote> existing = votes.findByCommentIdAndPlayerId(c.getId(), me.getId());
        String before = existing.map(CommentVote::getVote).orElse(null);
        String after;
        if (existing.isEmpty()) {
            votes.save(new CommentVote(c, me, vote));
            after = vote;
        } else if (before.equals(vote)) {
            votes.delete(existing.get());
            after = null;
        } else {
            existing.get().change(vote);
            after = vote;
        }
        votes.flush();
        int like = score(after, CommentVote.LIKE) - score(before, CommentVote.LIKE);
        int dislike = score(after, CommentVote.DISLIKE) - score(before, CommentVote.DISLIKE);
        long authorId = c.getPlayer().getId();
        comments.addVotes(c.getId(), like, dislike);
        players.addCommentScore(authorId, like - dislike); // 추천 +1, 비추천 −1. 추천 → 비추천은 −2
        events.publishEvent(new GameEvents.TitleCheck(authorId)); // 댓글 점수 칭호(4.10)
        Comment fresh = comments.findById(commentId).orElseThrow();
        return new CommentDtos.VoteResult(after, fresh.getLikeCount(), fresh.getDislikeCount());
    }

    private static int score(String vote, String kind) { return kind.equals(vote) ? 1 : 0; }

    // ---------------------------------------------------------------- 내부

    private Keyword requireKeyword(Player me, long keywordId) {
        return keywords.findById(keywordId)
                .filter(k -> sameCountry(me, k) && !CodeTables.KW_BANNED.equals(k.getKeywordState().getName()))
                .orElseThrow(() -> ApiException.notFound("KEYWORD_NOT_FOUND", "키워드를 찾을 수 없어요"));
    }

    private Comment requireMine(Player me, long commentId) {
        Comment c = comments.findById(commentId)
                .filter(x -> x.getPlayer().getId().equals(me.getId()))
                .orElseThrow(() -> ApiException.notFound("COMMENT_NOT_FOUND", "댓글을 찾을 수 없어요"));
        if (c.isDeleted()) throw ApiException.conflict("COMMENT_DELETED", "이미 삭제된 댓글이에요");
        return c;
    }

    private static boolean sameCountry(Player me, Keyword k) { return k.getCountry().getId().equals(me.getCountry().getId()); }

    private static boolean withdrawn(Player p) { return p.getUser().getDeletedAt() != null; }

    private boolean editable(Comment c) {
        return !c.isDeleted() && Instant.now(clock).isBefore(c.getCreatedAt().plus(EDIT_WINDOW));
    }

    private static String validBody(String raw) {
        String body = TextRules.clean(raw, true);
        TextRules.check(body, MIN_LENGTH, MAX_LENGTH, "COMMENT", "댓글");
        return body;
    }

    private List<CommentDtos.Item> items(Player me, List<Comment> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, String> mine = votes.findByPlayerIdAndCommentIdIn(me.getId(), list.stream().map(Comment::getId).toList())
                .stream().collect(Collectors.toMap(v -> v.getComment().getId(), CommentVote::getVote));
        return list.stream().map(c -> item(me, c, mine.get(c.getId()))).toList();
    }

    private CommentDtos.Item item(Player me, Comment c, String myVote) {
        boolean withdrawn = withdrawn(c.getPlayer());
        boolean mine = c.getPlayer().getId().equals(me.getId());
        String body = c.isDeleted() ? DELETED_TEXT : withdrawn ? WITHDRAWN_TEXT : c.getBody();
        // 추천 수는 조건부 UPDATE라 updated_at이 바뀌지 않는다. 작성 뒤 1초 넘게 지나 바뀌었으면 고친 것이다
        boolean edited = !c.isDeleted() && c.getUpdatedAt() != null
                && c.getUpdatedAt().isAfter(c.getCreatedAt().plusSeconds(1));
        return new CommentDtos.Item(c.getId(), withdrawn ? WITHDRAWN_NAME : c.getPlayer().getNickname(), body,
                c.getLikeCount(), c.getDislikeCount(), myVote, mine, mine && editable(c), c.isDeleted(), withdrawn, edited,
                c.getCreatedAt());
    }
}
