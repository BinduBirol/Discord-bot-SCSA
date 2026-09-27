package com.scsabot.discordbot.resources;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Handles the "/resource-post" slash command: posts one resource entry to the
 * configured channel immediately, instead of waiting for the cron schedule.
 * Wire it into SlashCommandListener's switch statement (see setup notes).
 */
@RequiredArgsConstructor
@Component
public class ResourcePostCommand {

    private final ResourcePoster poster;

    public void handleResourcePostCommand(SlashCommandInteractionEvent event) {
        // Acknowledge within Discord's 3-second window; only the requester sees this.
        event.deferReply(true).queue();

        ResourcePoster.Result result = poster.postOnce();

        String message = switch (result) {
            case POSTED -> "Posted one resource entry to the configured channel.";
            case NO_ENTRIES_LOADED -> "No resource entries are loaded - check the data/resources files.";
            case CHANNEL_NOT_CONFIGURED -> "No channel is configured (stamunity.resources.channel-id) - nothing was posted.";
            case CHANNEL_NOT_FOUND -> "Configured channel could not be found - is the bot still in that server?";
        };

        event.getHook().sendMessage(message).queue();
    }
}