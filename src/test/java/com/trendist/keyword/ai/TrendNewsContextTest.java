package com.trendist.keyword.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TrendNewsContextTest {
    @Test
    void sameSpellingInDifferentLanguagesDoesNotOverwrite() {
        var ctx = new TrendNewsContext();
        ctx.put("ko", "BTS", List.of("방탄소년단 새 앨범"));
        ctx.put("en", "bts", List.of("BTS announces new album"));
        assertThat(ctx.get("ko", "BTS")).containsExactly("방탄소년단 새 앨범");
        assertThat(ctx.get("en", "BTS")).containsExactly("BTS announces new album");
        assertThat(ctx.get("en", "unknown")).isEmpty();
    }
}
