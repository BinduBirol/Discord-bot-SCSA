package com.scsabot.discordbot.voice;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryVoiceTrackingService implements VoiceTrackingService {

    private final ConcurrentHashMap<String, ActiveVoiceSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<ActiveVoiceSession> trackJoin(String guildId, String userId, String channelId) {
        ActiveVoiceSession next = new ActiveVoiceSession(channelId, Instant.now());
        return Optional.ofNullable(sessions.put(key(guildId, userId), next));
    }

    @Override
    public Optional<ActiveVoiceSession> trackLeave(String guildId, String userId) {
        return Optional.ofNullable(sessions.remove(key(guildId, userId)));
    }

    private static String key(String guildId, String userId) {
        return guildId + ":" + userId;
    }
}
