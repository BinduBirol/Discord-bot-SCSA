package com.scsabot.discordbot.voice;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryVoiceTrackingServiceTest {

    @Test
    void trackLeaveReturnsTheActiveJoin() {
        InMemoryVoiceTrackingService tracking = new InMemoryVoiceTrackingService();
        tracking.trackJoin("guild", "user", "channel-1");

        var left = tracking.trackLeave("guild", "user");
        assertTrue(left.isPresent());
        assertEquals("channel-1", left.get().channelId());
        assertTrue(left.get().durationSeconds(Instant.now()) >= 0);
    }

    @Test
    void trackJoinReplacesPreviousSession() {
        InMemoryVoiceTrackingService tracking = new InMemoryVoiceTrackingService();
        tracking.trackJoin("guild", "user", "channel-1");
        var previous = tracking.trackJoin("guild", "user", "channel-2");

        assertTrue(previous.isPresent());
        assertEquals("channel-1", previous.get().channelId());

        var current = tracking.trackLeave("guild", "user");
        assertTrue(current.isPresent());
        assertEquals("channel-2", current.get().channelId());
    }

    @Test
    void trackLeaveWithoutJoinIsEmpty() {
        InMemoryVoiceTrackingService tracking = new InMemoryVoiceTrackingService();
        assertTrue(tracking.trackLeave("guild", "user").isEmpty());
    }
}
