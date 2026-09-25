package com.scsabot.discordbot.discord.command;

import com.scsabot.discordbot.story.StoryImageCommand;
import com.scsabot.discordbot.story.StoryWordsCommand;
import com.scsabot.discordbot.tabletopics.TableTopicCommand;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SlashCommandListener extends ListenerAdapter {

    private static final Logger log =
            LoggerFactory.getLogger(SlashCommandListener.class);

    private final EventCommandHandler eventCommandHandler;
    private final TableTopicCommand tableTopicCommand;
    private final StoryWordsCommand storyWordsCommand;
    private final StoryImageCommand storyImageCommand;

    public SlashCommandListener(
            EventCommandHandler eventCommandHandler,
            TableTopicCommand tableTopicCommand, StoryWordsCommand storyWordsCommand, StoryImageCommand storyImageCommand
    ) {
        this.eventCommandHandler = eventCommandHandler;
        this.tableTopicCommand = tableTopicCommand;
        this.storyWordsCommand = storyWordsCommand;
        this.storyImageCommand = storyImageCommand;
    }

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
                }
            }

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
