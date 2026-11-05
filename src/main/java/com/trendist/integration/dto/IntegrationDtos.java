package com.trendist.integration.dto;

/** 연동 바로가기(13.15). */
public final class IntegrationDtos {
    private IntegrationDtos() {}

    public record Item(String code, String label, String url) {}

    public record Request(String url) {}
}
