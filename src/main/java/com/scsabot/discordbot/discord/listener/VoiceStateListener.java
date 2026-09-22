package com.scsabot.discordbot.discord.listener;

import com.scsabot.discordbot.voice.ActiveVoiceSession;
import com.scsabot.discordbot.voice.VoiceActivityLogger;
import com.scsabot.discordbot.voice.VoiceTrackingService;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class VoiceStateListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(VoiceStateListener.class);

    private final VoiceTrackingService voiceTrackingService;
    private final VoiceActivityLogger voiceActivityLogger;
    private final TaskExecutor discordEventExecutor;

    public VoiceStateListener(
            VoiceTrackingService voiceTrackingService,
            VoiceActivityLogger voiceActivityLogger,
            @Qualifier("discordEventExecutor") TaskExecutor discordEventExecutor) {
        this.voiceTrackingService = voiceTrackingService;
        this.voiceActivityLogger = voiceActivityLogger;
        this.discordEventExecutor = discordEventExecutor;
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getChannelLeft() == null && event.getChannelJoined() != null) {
            discordEventExecutor.execute(() -> handleVoiceJoin(event));
            return;
        }
        if (event.getChannelLeft() != null && event.getChannelJoined() == null) {
            discordEventExecutor.execute(() -> handleVoiceLeave(event));
            return;
        }
        if (event.getChannelLeft() != null && event.getChannelJoined() != null) {
            discordEventExecutor.execute(() -> handleVoiceMove(event));
        }
    }

    private void handleVoiceJoin(GuildVoiceUpdateEvent event) {
        String guildId = event.getGuild().getId();
        String userId = event.getMember().getId();
        String channelId = event.getChannelJoined().getId();
        String channelName = event.getChannelJoined().getName();

        voiceTrackingService.trackJoin(guildId, userId, channelId);
        voiceActivityLogger.logJoin(event.getMember().getEffectiveName(), channelName);
        log.info("Member {} joined voice channel {}", userId, channelId);
    }

    private void handleVoiceLeave(GuildVoiceUpdateEvent event) {
        String guildId = event.getGuild().getId();
        String userId = event.getMember().getId();
        logLeave(event, voiceTrackingService.trackLeave(guildId, userId), userId);
    }

    private void handleVoiceMove(GuildVoiceUpdateEvent event) {
        String guildId = event.getGuild().getId();
        String userId = event.getMember().getId();

        logLeave(event, voiceTrackingService.trackLeave(guildId, userId), userId);

        String newChannelId = event.getChannelJoined().getId();
        String newChannelName = event.getChannelJoined().getName();
        voiceTrackingService.trackJoin(guildId, userId, newChannelId);
        voiceActivityLogger.logJoin(event.getMember().getEffectiveName(), newChannelName);
        log.info("Member {} joined new voice channel {}", userId, newChannelId);
    }

    private void logLeave(GuildVoiceUpdateEvent event, Optional<ActiveVoiceSession> session, String userId) {
        session.ifPresent(active -> {
            String channelName = "Unknown";
            var channel = event.getGuild().getChannelById(AudioChannel.class, active.channelId());
            if (channel != null) {
                channelName = channel.getName();
            } else if (event.getChannelLeft() != null) {
                channelName = event.getChannelLeft().getName();
            }

            long durationSeconds = active.durationSeconds(Instant.now());
            voiceActivityLogger.logLeave(event.getMember().getEffectiveName(), channelName, durationSeconds);
            log.info("Member {} left voice channel {} after {} seconds", userId, active.channelId(), durationSeconds);
        });
    }
}
