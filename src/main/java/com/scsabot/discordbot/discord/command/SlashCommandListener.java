package com.scsabot.discordbot.discord.command;

import com.scsabot.discordbot.moderation.ModerationCommand;
import com.scsabot.discordbot.practice.PracticeCommand;
import com.scsabot.discordbot.resources.ResourcePostCommand;
import com.scsabot.discordbot.timer.TimerCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class SlashCommandListener extends ListenerAdapter {

    private final EventCommandHandler eventCommandHandler;
    private final ResourcePostCommand resourcePostCommand;
    private final PracticeCommand practiceCommand;
    private final TimerCommand timerCommand;
    private final ModerationCommand moderationCommand;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {

        String commandName = event.getName();

        log.info(
                "Slash command received: {} by {}",
                commandName,
                event.getUser().getId()
        );

        switch (commandName) {

            case "ping" -> event.reply("Pong! The bot is online.")
                    .setEphemeral(true)
                    .queue();

            case "rules" -> event.reply(
                            "📜 **StamUnity Community Rules**\n\n" +
                                    "Please read our community rules here:\n" +
                                    "https://discord.com/channels/1543886876786565140/1556929839993331732"
                    )
                    .setEphemeral(true)
                    .queue();

            case "event" -> eventCommandHandler.handleEventCommand(event);

            case "resource-post" -> resourcePostCommand.handleResourcePostCommand(event);

            case "practice" -> practiceCommand.handlePracticeCommand(event);

            case "timer" -> timerCommand.handleTimer(event);

            case "warn" -> moderationCommand.handleWarn(event);

            case "ban" -> moderationCommand.handleBan(event);

            default -> event.reply("Unknown command.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {

        String modalId = event.getModalId();

        log.debug("Modal interaction received: {}", modalId);

        if (modalId.startsWith("event_create_modal")) {
            eventCommandHandler.handleEventCreateModal(event);
        }
    }
}