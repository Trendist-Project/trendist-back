package com.trendist.chat.job;

import com.trendist.chat.repository.ChatMessageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 매일: 30일 지난 채팅 삭제(4.13, 9.7). */
@Component
@RequiredArgsConstructor
public class ChatJobs {
    private static final Logger log = LoggerFactory.getLogger(ChatJobs.class);
    private static final Duration KEEP = Duration.ofDays(30);

    private final ChatMessageRepository messages;
    private final Clock clock;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void daily() {
        int n = messages.deleteOlderThan(Instant.now(clock).minus(KEEP));
        if (n > 0) log.info("chat cleanup: deleted={}", n);
    }
}
