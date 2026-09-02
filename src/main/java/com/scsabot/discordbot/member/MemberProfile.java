package com.scsabot.discordbot.member;

import java.time.Instant;

public record MemberProfile(
        String discordUserId,
        String guildId,
        String displayName,
        String country,
        String region,
        String stutteringLevel,
        Instant joinedAt,
        boolean active
) {
}
