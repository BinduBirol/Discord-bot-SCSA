package com.scsabot.discordbot.tabletopics;

import com.scsabot.discordbot.dto.TableTopicQuestion;
import com.scsabot.discordbot.dto.TableTopicSession;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;

@Component
public class TableTopicCommand extends ListenerAdapter {

    private final TableTopicService topicService;
    private final TableTopicSessionManager sessionManager;

    public TableTopicCommand(
            TableTopicService topicService,
            TableTopicSessionManager sessionManager
    ) {
        this.topicService = topicService;
        this.sessionManager = sessionManager;
    }

    @Override
    public void onSlashCommandInteraction(
            SlashCommandInteractionEvent event
    ) {

        if (!event.getName().equals("topic")) {
            return;
        }

        String category = event.getOption("category") != null
                ? event.getOption("category").getAsString()
                : null;

        String difficulty = event.getOption("difficulty") != null
                ? event.getOption("difficulty").getAsString()
                : null;

        TableTopicQuestion question =
                topicService.randomQuestion(
                        category,
                        difficulty,
                        null
                );

        if (question == null) {
            event.reply(
                    "❌ I couldn't find a topic matching those filters."
            ).setEphemeral(true).queue();
            return;
        }

        TableTopicSession session =
                new TableTopicSession(
                        category,
                        difficulty,
                        question
                );

        String sessionId =
                sessionManager.createSession(session);

        event.replyEmbeds(
                        buildEmbed(session).build()
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

    /**
     * Builds the Discord card shown to the user.
     */
    public EmbedBuilder buildEmbed(TableTopicSession session) {

        TableTopicQuestion question = session.getQuestion();

        EmbedBuilder embed = new EmbedBuilder();

        embed.setTitle("🗣️ Table Topic");

        StringBuilder description = new StringBuilder();

        description.append("**")
                .append(question.question())
                .append("**");

        if (session.getPracticeWord() != null) {
            description.append("\n\n🎲 **Random Word:** `")
                    .append(session.getPracticeWord())
                    .append("`");

            embed.setFooter(
                    "Tip: Try to link the word to your Table Topic answer."
            );
        }

        embed.setDescription(description.toString());

        return embed;
    }

    /**
     * Handles /topic when called directly by another command handler.
     */
    public void handleTopicCommand(
            SlashCommandInteractionEvent event
    ) {

        String category = event.getOption("category") != null
                ? event.getOption("category").getAsString()
                : null;

        String difficulty = event.getOption("difficulty") != null
                ? event.getOption("difficulty").getAsString()
                : null;

        TableTopicQuestion question =
                topicService.randomQuestion(
                        category,
                        difficulty,
                        null
                );

        if (question == null) {
            event.reply(
                    "❌ I couldn't find a topic matching those filters."
            ).setEphemeral(true).queue();
            return;
        }

        TableTopicSession session =
                new TableTopicSession(
                        category,
                        difficulty,
                        question
                );

        String sessionId =
                sessionManager.createSession(session);

        event.replyEmbeds(
                        buildEmbed(session).build()
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


}