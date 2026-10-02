package com.scsabot.discordbot.story;

import com.scsabot.discordbot.story.complete.StoryCompleteCommand;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Component
public class StoryButtonListener extends ListenerAdapter {

    private final StoryWordsCommand storyWordsCommand;
    private final StoryImageCommand storyImageCommand;
    private final StoryCompleteCommand storyCompleteCommand;

    public StoryButtonListener(
            StoryWordsCommand storyWordsCommand,
            StoryImageCommand storyImageCommand,
            StoryCompleteCommand storyCompleteCommand
    ) {
        this.storyWordsCommand = storyWordsCommand;
        this.storyImageCommand = storyImageCommand;
        this.storyCompleteCommand = storyCompleteCommand;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {

        String componentId = event.getComponentId();

        if (!componentId.startsWith("story:")) {
            return;
        }

        String[] parts = componentId.split(":");

        if (parts.length != 3) {
            return;
        }

        String type = parts[1];
        String action = parts[2];

        switch (type) {

            case "words" -> handleWords(event, action);

            case "image" -> handleImage(event, action);

            case "complete" -> handleComplete(event, action);

            default -> {
                // Ignore unknown story types.
            }
        }
    }

    private void handleWords(ButtonInteractionEvent event, String action) {
        switch (action) {
            case "regenerate" -> storyWordsCommand.handleRegenerate(event);
            case "newspeaker" -> storyWordsCommand.handleNewSpeaker(event);
            case "timer" -> storyWordsCommand.handleTimer(event);
            default -> {
                // Ignore unknown words actions.
            }
        }
    }

    private void handleImage(ButtonInteractionEvent event, String action) {
        switch (action) {
            case "regenerate" -> storyImageCommand.handleRegenerate(event);
            case "newspeaker" -> storyImageCommand.handleNewSpeaker(event);
            case "timer" -> storyImageCommand.handleTimer(event);
            default -> {
                // Ignore unknown image actions.
            }
        }
    }

    private void handleComplete(ButtonInteractionEvent event, String action) {
        switch (action) {
            case "regenerate" -> storyCompleteCommand.handleRegenerate(event);
            case "nextspeaker" -> storyCompleteCommand.handleNextSpeaker(event);
            case "timer" -> storyCompleteCommand.handleTimer(event);
            default -> {
                // Ignore unknown complete actions.
            }
        }
    }
}