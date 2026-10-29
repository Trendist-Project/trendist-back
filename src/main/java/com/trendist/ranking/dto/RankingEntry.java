package com.trendist.ranking.dto;

public record RankingEntry(int rank, long playerId, String nickname, int level, long score) {}
