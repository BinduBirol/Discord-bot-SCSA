package com.scsabot.discordbot.dto;

import java.util.List;

public record RolePlayScenario(
        String id,
        String title,
        String category,
        int playerCount,
        String situation,
        List<Character> characters,
        List<DialogueLine> dialogue
) {
    public record Character(
            String id,      // "PLAYER_1" / "PLAYER_2"
            String role,
            String goal
    ) {
    }

    public record DialogueLine(
            int step,
            String speaker, // matches Character.id
            LineType type,
            String text
    ) {
    }

    public enum LineType {
        EXACT,  // the player reads this line as written
        PROMPT  // the player improvises based on the instruction
    }
}