package com.trendist.comment.service;

import com.trendist.comment.dto.CommentDtos;
import java.util.List;

/** 키워드 댓글과 투표(4.12, 9.6). 모두 내 나라 키워드만 다룬다. */
public interface CommentService {
    /** 추천 상위 3개(🥇🥈🥉). */
    List<CommentDtos.Item> top(long userId, long keywordId);

    /** 최신순 페이지(추천 상위 3개는 뺀다). size는 최대 50. */
    CommentDtos.Page latest(long userId, long keywordId, int page, int size);

    /** 쓰기: 200자까지(빈 댓글만 안 된다), 링크·전화번호 불가. 댓글 점수 +1. */
    CommentDtos.Item write(long userId, long keywordId, String body);

    /** 고치기: 내 댓글, 작성 후 5분 안. */
    CommentDtos.Item edit(long userId, long commentId, String body);

    /** 지우기(소프트 삭제): 내 댓글, 언제든. 댓글 점수 −1(이미 받은 추천·비추천 점수는 그대로). */
    void delete(long userId, long commentId);

    /**
     * 투표 토글: 없음 → 추가, 같은 값 → 취소, 반대 값 → 변경. 자기 댓글과 삭제·탈퇴 댓글은 안 된다.
     * 작성자의 댓글 점수도 같이 바꾼다(추천 +1, 비추천 −1, 0 아래 없음).
     */
    CommentDtos.VoteResult vote(long userId, long commentId, String vote);
}
