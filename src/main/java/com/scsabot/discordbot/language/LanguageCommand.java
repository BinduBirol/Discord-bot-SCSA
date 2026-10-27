package com.scsabot.discordbot.language;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

/**
 * Main /language router. Delegates to the subcommand handlers.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LanguageCommand {

    private final LanguageSetupSubcommand setupSubcommand;
    private final LanguageShowSubcommand showSubcommand;

    public void handleLanguageCommand(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        log.info("Language subcommand: {}", sub);

        if (sub == null) {
            event.reply("Please choose a language subcommand.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        switch (sub) {
            case "setup" -> setupSubcommand.handleSetupCommand(event);
            case "show" -> showSubcommand.handleShowCommand(event);
            default -> event.reply("Unknown language subcommand.")
                    .setEphemeral(true)
                    .queue();
        }
    }
}
