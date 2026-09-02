package com.scsabot.discordbot.discord.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DiscordCommandRegistrar {

    private static final Logger log =
            LoggerFactory.getLogger(DiscordCommandRegistrar.class);

    private final JDA jda;

    public DiscordCommandRegistrar(JDA jda) {
        this.jda = jda;
    }

    public void registerCommands() {

        List<SlashCommandData> commands = List.of(
                Commands.slash("ping", "Check if the bot is responsive."),
                Commands.slash("rules", "Display the server rules.")
        );

        jda.updateCommands()
                .addCommands(commands)
                .queue(
                        success -> log.info(
                                "Registered {} slash commands",
                                commands.size()
                        ),
                        failure -> log.error(
                                "Failed to register slash commands",
                                failure
                        )
                );
    }
}