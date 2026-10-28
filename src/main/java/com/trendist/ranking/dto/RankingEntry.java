package com.trendist.ranking.dto;

import java.math.BigDecimal;

public record RankingEntry(int rank, long playerId, String nickname, int level, BigDecimal score) {}
