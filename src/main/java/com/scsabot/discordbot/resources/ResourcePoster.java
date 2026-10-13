package com.scsabot.discordbot.resources;

import com.scsabot.discordbot.dto.ResourceEntry;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


@Slf4j
@Component
public class ResourcePoster {

    // TODO: replace these with your actual channel IDs
    private static final List<String> CHANNEL_IDS = List.of(
            "1553499664702115921",
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