package com.scsabot.discordbot.story;


import com.scsabot.discordbot.dto.StoryImage;

public interface ImageProvider {

    /**
     * Returns null if not configured, request fails, or no results.
     */
    StoryImage fetchRandomImage();

    /**
     * False when the API key is missing, so the picker can skip it.
     */
    boolean isConfigured();
}