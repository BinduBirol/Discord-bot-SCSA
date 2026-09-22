package com.scsabot.discordbot.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

public final class EventDateTimes {

    public static final ZoneId INPUT_ZONE = ZoneId.of("Asia/Dhaka");

    private EventDateTimes() {
    }

    public static Instant parse(String dateStr, String timeStr) throws DateTimeParseException {
        LocalDateTime localDateTime = LocalDateTime.parse(dateStr + "T" + timeStr + ":00");
        return ZonedDateTime.of(localDateTime, INPUT_ZONE).toInstant();
    }

    public static String discordTimestamp(Instant instant) {
        return "<t:" + instant.getEpochSecond() + ":F>";
    }
}
