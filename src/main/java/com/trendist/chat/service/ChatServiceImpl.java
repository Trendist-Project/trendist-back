package com.trendist.chat.service;

import com.trendist.chat.entity.ChatMessage;
import com.trendist.chat.repository.ChatMessageRepository;
import com.trendist.common.support.TextRules;
import com.trendist.country.repository.CountryRepository;
import com.trendist.player.repository.PlayerRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private static final int MAX_LENGTH = 100;

    private final ChatMessageRepository messages;
    private final CountryRepository countries;
    private final PlayerRepository players;
    private final Clock clock;

    @Override
    public String validate(String raw) {
        String text = TextRules.clean(raw, false).replaceAll("\\s{2,}", " ");
        TextRules.check(text, 1, MAX_LENGTH, "CHAT", "메시지");
        return text;
    }

    @Override
    @Transactional
    public Saved save(long countryId, long playerId, String text) {
        Instant now = Instant.now(clock);
        ChatMessage m = messages.save(new ChatMessage(countries.getReferenceById(countryId), players.getReferenceById(playerId), text, now));
        return new Saved(m.getId(), now.toEpochMilli());
    }
}
