package com.scsabot.discordbot.speakers;

import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers when each user connected to their current voice channel,
 * because Discord does not expose that. Used to sort the speaker list.
 */
@Component
public class VoiceJoinTimeTracker extends ListenerAdapter {

    private final Map<Long, Instant> joinTimes = new ConcurrentHashMap<>();

    /**
     * Unknown join time sorts first (the user was connected before we started tracking).
     */
    public Instant getJoinTime(long userId) {
        return joinTimes.getOrDefault(userId, Instant.EPOCH);
    }

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        // Users already in voice when the bot starts share the startup time.
        Instant now = Instant.now();
        event.getGuild().getVoiceStates().forEach(state -> {
            if (state.inAudioChannel()) {
                joinTimes.putIfAbsent(state.getMember().getIdLong(), now);
            }
        });
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        long userId = event.getMember().getIdLong();

        if (event.getChannelJoined() != null) {
            // Joined, or moved to another channel - either way the clock restarts.
            joinTimes.put(userId, Instant.now());
        } else {
            joinTimes.remove(userId);
        }
    }
}
