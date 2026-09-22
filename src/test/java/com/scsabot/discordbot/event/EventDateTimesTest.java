package com.scsabot.discordbot.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventDateTimesTest {

    @Test
    void parseInterpretsDateAndTimeInAsiaDhaka() {
        Instant instant = EventDateTimes.parse("2026-09-12", "22:00");
        Instant expected = ZonedDateTime.of(LocalDateTime.of(2026, 9, 12, 22, 0), EventDateTimes.INPUT_ZONE).toInstant();
        assertEquals(expected, instant);
    }

    @Test
    void discordTimestampUsesEpochSeconds() {
        Instant instant = Instant.ofEpochSecond(1_778_000_000L);
        assertEquals("<t:1778000000:F>", EventDateTimes.discordTimestamp(instant));
    }

    @Test
    void parseRejectsInvalidDate() {
        assertThrows(Exception.class, () -> EventDateTimes.parse("12-09-2026", "22:00"));
    }
}
