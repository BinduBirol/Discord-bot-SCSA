package com.scsabot.discordbot.voice;

import java.time.Duration;
import java.time.Instant;

public record ActiveVoiceSession(String channelId, Instant joinedAt) {

    public long durationSeconds(Instant leftAt) {
        Instant end = leftAt != null ? leftAt : Instant.now();
        return Math.max(0, Duration.between(joinedAt, end).toSeconds());
    }
}
