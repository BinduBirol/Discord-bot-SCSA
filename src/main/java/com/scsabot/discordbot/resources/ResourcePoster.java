package com.scsabot.discordbot.resources;

import com.scsabot.discordbot.dto.ResourceEntry;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds and sends the embed for one resource entry. Both ResourcePostScheduler
 * (the twice-a-day cron job) and ResourcePostCommand (the /resource-post
 * slash command) call postOnce() so the actual posting logic lives in one place.
 * <p>
 * A random channel is picked each time from CHANNEL_IDS below.
 * <p>
 * JDA is injected with @Lazy on purpose: JdaConfig builds the JDA bean using
 * SlashCommandListener as an event listener, and SlashCommandListener depends
 * on ResourcePostCommand -> ResourcePoster -> JDA. Without @Lazy that's a
 * circular dependency Spring can't resolve. @Lazy hands this class a proxy
 * instead of the real JDA bean, so JDA can finish building first; the proxy
 * resolves to the real instance the first time postOnce() actually uses it.
 */
@Slf4j
@Component
public class ResourcePoster {

    // TODO: replace these with your actual channel IDs
    private static final List<String> CHANNEL_IDS = List.of(
            "1553499664702115921",
            "1548374471730266222",
            "1543886877512175715"
    );

    private final JDA jda;
    private final ResourcePickerService picker;
    private final ResourceBotProperties properties;

    public ResourcePoster(@Lazy JDA jda, ResourcePickerService picker, ResourceBotProperties properties) {
        this.jda = jda;
        this.picker = picker;
        this.properties = properties;
    }

    /**
     * What happened when postOnce() ran, so callers (like the slash command) can reply appropriately.
     */
    public enum Result {
        POSTED,
        NO_ENTRIES_LOADED,
        CHANNEL_NOT_CONFIGURED,
        CHANNEL_NOT_FOUND
    }

    public Result postOnce() {
        ResourceEntry entry = picker.next();
        if (entry == null) {
            log.warn("No resource entries loaded - check {} for files matching {}.",
                    properties.getDirectory(), properties.getFilePattern());
            return Result.NO_ENTRIES_LOADED;
        }

        if (CHANNEL_IDS.isEmpty()) {
            log.error("CHANNEL_IDS is empty - cannot post.");
            return Result.CHANNEL_NOT_CONFIGURED;
        }

        String channelId = CHANNEL_IDS.get(ThreadLocalRandom.current().nextInt(CHANNEL_IDS.size()));

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            log.error("Could not find a text channel with id {}. Is the bot in that server?", channelId);
            return Result.CHANNEL_NOT_FOUND;
        }

        String message = """
                **%s**
                
                %s
                
                _%s \u2022 Source: %s_""".formatted(
                entry.getTitle(),
                entry.getBody(),
                entry.getCategory(),
                entry.getSource()
        );

        channel.sendMessage(message).queue(
                success -> log.info("Posted '{}' to channel {}", entry.getTitle(), channelId),
                error -> log.error("Failed to post to Discord: {}", error.getMessage())
        );

        return Result.POSTED;
    }
}