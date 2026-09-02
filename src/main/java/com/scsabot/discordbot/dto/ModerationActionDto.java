package com.scsabot.discordbot.dto;

public record ModerationActionDto(
        String memberId,
        String moderatorId,
        String actionType,
        String reason,
        String metadata
) {
}
