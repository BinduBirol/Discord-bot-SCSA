package com.scsabot.discordbot.discord.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DiscordBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(DiscordBootstrapService.class);

    public void onReady() {
        log.info("Discord bot bootstrap completed. Ready for feature extensions.");
    }
}
