package com.scsabot.discordbot.resources;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs on the cron schedule from stamunity.resources.cron (default: 9:00 and
 * 21:00 daily -> "twice a day") and posts one entry via ResourcePoster.
 */
@RequiredArgsConstructor
@Component
public class ResourcePostScheduler {

    private final ResourcePoster poster;

    @Scheduled(cron = "#{@resourceBotProperties.cron}", zone = "Asia/Dhaka")
    public void postScheduledResource() {
        poster.postOnce();
    }
}