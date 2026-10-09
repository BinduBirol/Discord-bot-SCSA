package com.scsabot.discordbot.practice;

import com.scsabot.discordbot.service.RoleService;
import com.scsabot.discordbot.story.StoryImageCommand;
import com.scsabot.discordbot.story.StoryWordsCommand;
import com.scsabot.discordbot.story.complete.StoryCompleteCommand;
import com.scsabot.discordbot.tabletopics.TableTopicCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
@Component
public class PracticeCommand {

    private static final String ROLE_KEY = "practice";

    private final TableTopicCommand tableTopicCommand;
    private final StoryWordsCommand storyWordsCommand;
    private final StoryImageCommand storyImageCommand;
    private final StoryCompleteCommand storyCompleteCommand;
    private final RoleService roleService;

    public void handlePracticeCommand(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        log.info("Practice subcommand: {}", sub);

        if (sub == null) {
            event.reply("Please choose a practice type.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        switch (sub) {
            case "random" -> handleRandom(event);
            case "topic" -> tableTopicCommand.handleTopicCommand(event);
            case "words" -> storyWordsCommand.handleStoryCommand(event);
            case "image" -> storyImageCommand.handleStoryCommand(event);
            case "story" -> storyCompleteCommand.handleCompleteCommand(event);
            default -> {
                event.reply("Unknown practice type.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
        }

        roleService.grantRoles(event.getMember(), ROLE_KEY);
    }

    private void handleRandom(SlashCommandInteractionEvent event) {
        List<Consumer<SlashCommandInteractionEvent>> exercises = List.of(
                tableTopicCommand::handleTopicCommand,
                storyWordsCommand::handleStoryCommand,
                storyImageCommand::handleStoryCommand,
                storyCompleteCommand::handleCompleteCommand
        );

        int index = ThreadLocalRandom.current().nextInt(exercises.size());
        log.info("Practice picked exercise index {}", index);
        exercises.get(index).accept(event);
    }
}