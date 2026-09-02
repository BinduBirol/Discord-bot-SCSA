package com.scsabot.discordbot.discord.listener;

import com.scsabot.discordbot.voice.VoiceActivityLogger;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BotStatusListener extends ListenerAdapter {

    private static final Logger log =
            LoggerFactory.getLogger(BotStatusListener.class);

    private final VoiceActivityLogger voiceActivityLogger;

    public BotStatusListener(VoiceActivityLogger voiceActivityLogger) {
        this.voiceActivityLogger = voiceActivityLogger;
    }

    @Override
    public void onReady(ReadyEvent event) {

        log.info("Discord bot is online.");

        voiceActivityLogger.logBotOnline();
    }

    @Override
    public void onShutdown(ShutdownEvent event) {

        log.info("Discord bot is going offline.");

        voiceActivityLogger.logBotOffline();
    }
}