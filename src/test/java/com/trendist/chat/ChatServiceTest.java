package com.trendist.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trendist.chat.service.ChatServiceImpl;
import com.trendist.common.exception.ApiException;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class ChatServiceTest {
    // 검사만 쓰므로 저장소는 비워 둔다
    private final ChatServiceImpl chat = new ChatServiceImpl(null, null, null, Clock.systemUTC());

    @Test
    void cleansAndLimits() {
        assertThat(chat.validate("  안녕   #양쯔깐루\n 먹어봄  ")).isEqualTo("안녕 #양쯔깐루 먹어봄");
        assertThatThrownBy(() -> chat.validate("   ")).isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "CHAT_LENGTH");
        assertThatThrownBy(() -> chat.validate("가".repeat(101))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> chat.validate("https://a.b 들어와")).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CHAT_LINK");
        assertThatThrownBy(() -> chat.validate("연락 010-1234-5678")).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CHAT_PHONE");
    }
}
