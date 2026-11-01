package com.scsabot.discordbot.discord.command;

import com.scsabot.discordbot.language.LanguageCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.InteractionContextType;

import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnBean(JDA.class)
public class DiscordCommandRegistrar {

    private static final String GUILD_ID = "1543886876786565140";

    private final JDA jda;
    private final LanguageCommand languageCommand;

    public void registerCommands() {

        List<SlashCommandData> commands = List.of(
                Commands.slash("ping", "Check if the bot is responsive."),

                Commands.slash("rules", "Display the server rules."),

                Commands.slash("event", "Manage community events")
                        .addSubcommands(

                                new SubcommandData(
                                        "upcoming",
                                        "Show the next upcoming event"
                                ),
                                new SubcommandData(
                                        "announce",
                                        "Announce the event in event-update channel"
                                ),
                                new SubcommandData("import", "Import Discord Scheduled Events")
                        ),

                Commands.slash("resource-post", "Immediately post one resource entry to the configured channel")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER)),


                Commands.slash("practice", "Speaking practice exercises")
                        .addSubcommands(
                                new SubcommandData("random", "Get a random practice exercise"),
                                new SubcommandData("topic", "Get a random speaking practice topic")
                                        .addOptions(new OptionData(OptionType.STRING, "difficulty", "Difficulty level", false)
                                                .addChoice("Easy", "easy")
                                                .addChoice("Medium", "medium")
                                                .addChoice("Hard", "hard")),
                                new SubcommandData("words", "Get 3 random words for story speaking practice"),
                                new SubcommandData("image", "Get a random image for story speaking practice"),

                                new SubcommandData("story", "Finish a story that the bot starts"),
                                new SubcommandData("roleplay", "Practice a conversation with friends")
                                        .addOption(OptionType.USER, "user1", "First player", true)
                                        .addOption(OptionType.USER, "user2", "Second player", true)
                                        .addOption(OptionType.USER, "user3", "Third player (optional)", false),
                                new SubcommandData("read", "Get a random paragraph and tongue-twisters")
                                        .addOptions(new OptionData(OptionType.STRING, "lang", "Language", false)
                                                .addChoice("English", "english")
                                                .addChoice("Bangla", "bangla")
                                                .addChoice("Hindi", "hindi"))
                                        .addOptions(new OptionData(OptionType.STRING, "difficulty", "Difficulty level", false)
                                                .addChoice("Easy", "easy")
                                                .addChoice("Medium", "medium")
                                                .addChoice("Hard", "hard"))
                        ),

                Commands.slash("timer", "Start a countdown timer")
                        .addOption(OptionType.INTEGER, "minutes", "How long (1-60)", true)
                        .addOption(OptionType.STRING, "label", "Message when time is up", false),

                Commands.slash("warn", "Warn a user and DM them")
                        .addOption(OptionType.USER, "user", "User to warn", true)
                        .addOption(OptionType.STRING, "reason", "Reason for the warning", false)
                        .setContexts(InteractionContextType.GUILD)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS)),

                Commands.slash("ban", "Ban a user and DM them")
                        .addOption(OptionType.USER, "user", "User to ban", true)
                        .addOption(OptionType.STRING, "reason", "Reason for the ban", false)
                        .setContexts(InteractionContextType.GUILD)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)),

                Commands.slash("language", "Manage spoken languages")
                        .addSubcommands(new SubcommandData("setup", "Create or update the language selection panel"),
                                new SubcommandData("show", "Show available and selected spoken languages"))


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