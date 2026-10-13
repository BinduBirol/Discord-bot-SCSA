package com.scsabot.discordbot.roles.interest;

import java.util.List;
import java.util.Optional;

/**
 * The interest roles members can pick. Role name format: "🎵 Music".
 */
public final class Interests {

    public record Interest(String code, String emoji, String name) {
        public String roleName() {
            return emoji + " " + name;
        }
    }

    /** Max 20 per message (Discord reaction limit); the listener splits automatically if this grows. */
    public static final List<Interest> ALL = List.of(
            new Interest("music", "🎵", "Music"),
            new Interest("gaming", "🎮", "Gaming"),
            new Interest("movies-tv", "🎬", "Movies & TV"),
            new Interest("casual", "☕", "Casual Hangouts"),
            new Interest("study", "📚", "Study & Research"),
            new Interest("tech", "💻", "Technology & Programming"),
            new Interest("books", "📖", "Books & Writing"),
            new Interest("psychology", "🧠", "Psychology & Self-Improvement"),
            new Interest("speaking", "🎤", "Public Speaking & Debate"),
            new Interest("fitness", "🏃", "Fitness & Sports"),
            new Interest("photo-art", "📸", "Photography & Art"),
            new Interest("travel", "\u2708\uFE0F", "Travel & Culture"),
            new Interest("puzzles", "🧩", "Puzzles & Trivia"),
            new Interest("creative", "🎨", "Creative Arts"),
            new Interest("cooking", "🍳", "Cooking & Food"),
            new Interest("sports", "⚽", "Sports"),
            new Interest("memes", "😂", "Memes & Humor"),
            new Interest("languages", "🌍", "Languages & Culture"),
            new Interest("career", "💼", "Career & Professional Growth"),
            new Interest("board-games", "🎲", "Board Games & Trivia"));

    private Interests() {
    }

    /** Matches ignoring the emoji variation selector (U+FE0F), which Discord may add or drop. */
    public static Optional<Interest> fromEmoji(String emoji) {
        String key = strip(emoji);
        return ALL.stream().filter(i -> strip(i.emoji()).equals(key)).findFirst();
    }

    private static String strip(String s) {
        return s.replace("\uFE0F", "");
    }
}
