package com.scsabot.discordbot.discord.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SlashCommandListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(SlashCommandListener.class);

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        log.info("Slash command received: {} by {}", commandName, event.getUser().getId());

        switch (commandName) {
            case "ping" -> event.reply("Pong! The bot is online.").setEphemeral(true).queue();
            case "rules" -> event.reply("1. Be respectful and kind.\n2. No harassment or discrimination.\n3. Keep discussions relevant to the stuttering support community.\n4. Use channels appropriately.\n5. Follow moderator instructions.")
                    .setEphemeral(true)
                    .queue();
            default -> event.reply("Unknown command.").setEphemeral(true).queue();
        }
    }
}
