package com.scsabot.discordbot.dto;

public record StoryImage(
        String imageUrl,
        String photographer,
        String pageUrl,
        String sourceName   // "Pexels" or "Unsplash"
) {
}