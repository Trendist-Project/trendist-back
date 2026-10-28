package com.trendist.auth.dto;

/** 액세스 토큰과 남은 수명(초). */
public record TokenResponse(String accessToken, long expiresIn) {}
