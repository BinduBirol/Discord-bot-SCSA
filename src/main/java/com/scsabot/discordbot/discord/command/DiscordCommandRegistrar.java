package com.scsabot.discordbot.discord.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnBean(JDA.class)
public class DiscordCommandRegistrar {

    private static final Logger log =
            LoggerFactory.getLogger(DiscordCommandRegistrar.class);

    private static final String GUILD_ID = "1543886876786565140";

    private final JDA jda;

    public DiscordCommandRegistrar(JDA jda) {
        this.jda = jda;
    }

    public void registerCommands() {

        List<SlashCommandData> commands = List.of(
                Commands.slash("ping", "Check if the bot is responsive."),

                Commands.slash("rules", "Display the server rules."),

                Commands.slash("event", "Manage community events")
                        .addSubcommands(
                                new SubcommandData(
                                        "create",
                                        "Create a new community event"
                                ),
                                new SubcommandData(
                                        "list",
                                        "List upcoming events"
                                ),
                                new SubcommandData(
                                        "upcoming",
                                        "Show the next upcoming event"
                                ),
                                new SubcommandData("import", "Import Discord Scheduled Events")
                        ),
                Commands.slash("topic", "Get a random speaking practice topic")
                        .addOption(OptionType.STRING, "category",
                                "Optional topic category", false)
                        .addOption(OptionType.STRING, "difficulty",
                                "Optional difficulty: easy, medium, hard", false),
                Commands.slash("story", "Story speaking practice")
                        .addSubcommands(
                                new SubcommandData(
                                        "words",
                                        "Get 3 random words for story speaking practice"
                                ),
                                new SubcommandData(
                                        "image",
                                        "Get a random image for story speaking practice"
                                )
                        )

        );

        var guild = jda.getGuildById(GUILD_ID);

        if (guild == null) {
            log.error("Could not find guild {}", GUILD_ID);
            return;
        }

        guild.updateCommands()
                .addCommands(commands)
                .queue(
                        success -> log.info(
                                "Registered {} slash commands to guild {}",
                                commands.size(),
                                GUILD_ID
                        ),
                        failure -> log.error(
                                "Failed to register slash commands",
                                failure
                        )
                );
    }
}