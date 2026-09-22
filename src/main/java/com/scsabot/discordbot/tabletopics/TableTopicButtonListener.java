package com.scsabot.discordbot.tabletopics;

import com.scsabot.discordbot.dto.TableTopicQuestion;
import com.scsabot.discordbot.dto.TableTopicSession;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;

@Component
public class TableTopicButtonListener extends ListenerAdapter {

    private final TableTopicService topicService;
    private final TableTopicSessionManager sessionManager;
    private final TableTopicCommand topicCommand;

    public TableTopicButtonListener(
            TableTopicService topicService,
            TableTopicSessionManager sessionManager,
            TableTopicCommand topicCommand
    ) {
        this.topicService = topicService;
        this.sessionManager = sessionManager;
        this.topicCommand = topicCommand;
    }

    @Override
    public void onButtonInteraction(
            ButtonInteractionEvent event
    ) {

        String componentId = event.getComponentId();

        if (!componentId.startsWith("tabletopic:")) {
            return;
        }

        String[] parts = componentId.split(":");

        if (parts.length != 3) {
            return;
        }

        String action = parts[1];
        String sessionId = parts[2];

        TableTopicSession session =
                sessionManager.getSession(sessionId);

        if (session == null) {

            event.reply(
                            "This Table Topic session has expired."
                    )
                    .setEphemeral(true)
                    .queue();

            return;
        }

        switch (action) {

            case "next" -> handleNext(event, session, sessionId);

            case "word" -> handleWord(event, session);

            default -> {
                // Ignore unknown TableTopic buttons.
            }
        }
    }

    private void handleNext(
            ButtonInteractionEvent event,
            TableTopicSession session,
            String sessionId
    ) {

        TableTopicQuestion nextQuestion =
                topicService.randomQuestion(
                        session.getCategory(),
                        session.getDifficulty(),
                        session.getQuestion()
                );

        if (nextQuestion == null) {

            event.reply(
                            "❌ No other questions match the current filters."
                    )
                    .setEphemeral(true)
                    .queue();

            return;
        }

        session.setQuestion(nextQuestion);

        event.replyEmbeds(
                        topicCommand.buildEmbed(session).build()
                )
                .addActionRow(
                        Button.primary(
                                "tabletopic:next:" + sessionId,
                                "➡️ Next Prompt"
                        ),
                        Button.secondary(
                                "tabletopic:word:" + sessionId,
                                "🎲 Word"
                        )
                )
                .queue();
    }

    private void handleWord(
            ButtonInteractionEvent event,
            TableTopicSession session
    ) {

        String word = topicService.randomWord();

        if (word == null) {

            event.reply(
                            "❌ No practice words are available."
                    )
                    .setEphemeral(true)
                    .queue();

            return;
        }

        session.setPracticeWord(word);

        event.editMessageEmbeds(
                topicCommand.buildEmbed(session).build()
        ).queue();
    }
}