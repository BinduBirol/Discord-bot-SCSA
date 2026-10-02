package com.scsabot.discordbot.discord.command;

import com.scsabot.discordbot.practice.PracticeCommand;
import com.scsabot.discordbot.resources.ResourcePostCommand;
import com.scsabot.discordbot.story.StoryImageCommand;
import com.scsabot.discordbot.story.StoryWordsCommand;
import com.scsabot.discordbot.story.complete.StoryCompleteCommand;
import com.scsabot.discordbot.tabletopics.TableTopicCommand;
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
    private final TableTopicCommand tableTopicCommand;
    private final StoryWordsCommand storyWordsCommand;
    private final StoryImageCommand storyImageCommand;
    private final ResourcePostCommand resourcePostCommand;
    private final PracticeCommand practiceCommand;
    private final StoryCompleteCommand storyCompleteCommand;
    private final TimerCommand timerCommand;

    @Override
    public void onSlashCommandInteraction(
            SlashCommandInteractionEvent event
    ) {

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
                            "1. Be respectful and kind.\n" +
                                    "2. No harassment or discrimination.\n" +
                                    "3. Keep discussions relevant to the stuttering support community.\n" +
                                    "4. Use channels appropriately.\n" +
                                    "5. Follow moderator instructions."
                    )
                    .setEphemeral(true)
                    .queue();

            case "event" -> eventCommandHandler.handleEventCommand(event);

            case "topic" -> tableTopicCommand.handleTopicCommand(event);

            case "story" -> {
                if ("words".equals(event.getSubcommandName())) {
                    storyWordsCommand.handleStoryCommand(event);
                } else if ("image".equals(event.getSubcommandName())) {
                    storyImageCommand.handleStoryCommand(event);
                } else if ("complete".equals(event.getSubcommandName())) {
                    storyCompleteCommand.handleCompleteCommand(event);
                }
            }

            case "resource-post" -> resourcePostCommand.handleResourcePostCommand(event);

            case "practice" -> practiceCommand.handlePracticeCommand(event);

            case "timer" -> timerCommand.handleTimer(event);

            default -> event.reply("Unknown command.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {

        String modalId = event.getModalId();

        log.debug(
                "Modal interaction received: {}",
                modalId
        );

        if (modalId.startsWith("event_create_modal")) {
            eventCommandHandler.handleEventCreateModal(event);
        }
    }
}