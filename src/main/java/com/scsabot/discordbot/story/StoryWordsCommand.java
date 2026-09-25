package com.scsabot.discordbot.story;

import com.scsabot.discordbot.tabletopics.TableTopicDataLoader;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /story words - Story speaking practice.
 * Pulls 3 random words from the words loaded by {@link TableTopicDataLoader}
 * (data/words/*.json) and challenges the user to speak a short, connected
 * story that uses all three.
 * <p>
 * Two buttons:
 * - "🔄 Regenerate" edits the current message with a new set of words,
 * for the same speaker who wants different words.
 * - "🗣️ New Speaker" posts a brand new message with its own words,
 * leaving the previous speaker's message untouched.
 */
@Component
public class StoryWordsCommand extends ListenerAdapter {

    private static final String COMMAND_NAME = "story";
    private static final String SUBCOMMAND_NAME = "words";
    private static final String REGENERATE_BUTTON_ID = "story:words:regenerate";
    private static final String NEW_SPEAKER_BUTTON_ID = "story:words:newspeaker";
    private static final int WORD_COUNT = 3;

    private final TableTopicDataLoader dataLoader;

    public StoryWordsCommand(TableTopicDataLoader dataLoader) {
        this.dataLoader = dataLoader;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(COMMAND_NAME)
                || !SUBCOMMAND_NAME.equals(event.getSubcommandName())) {
            return;
        }

        List<String> words = pickRandomWords();

        if (words.isEmpty()) {
            event.reply(
                    "❌ No practice words are loaded yet — check data/words."
            ).setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(buildEmbed(words).build())
                .addActionRow(actionRow())
                .queue();
    }

    /**
     * Handles /story words when called directly by SlashCommandListener.
     */
    public void handleStoryCommand(SlashCommandInteractionEvent event) {

        List<String> words = pickRandomWords();

        if (words.isEmpty()) {
            event.reply(
                    "❌ No practice words are loaded yet — check data/words."
            ).setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(buildEmbed(words).build())
                .addActionRow(actionRow())
                .queue();
    }

    /**
     * "🔄 Regenerate" - same speaker, edit the existing message in place.
     */
    public void handleRegenerate(ButtonInteractionEvent event) {

        List<String> words = pickRandomWords();

        if (words.isEmpty()) {
            event.reply(
                    "❌ No practice words are loaded yet — check data/words."
            ).setEphemeral(true).queue();
            return;
        }

        event.editMessageEmbeds(buildEmbed(words).build())
                .setActionRow(actionRow())
                .queue();
    }

    /**
     * "🗣️ New Speaker" - post a brand new message with its own words,
     * leaving the previous message (and its words) untouched.
     */
    public void handleNewSpeaker(ButtonInteractionEvent event) {

        List<String> words = pickRandomWords();

        if (words.isEmpty()) {
            event.reply(
                    "❌ No practice words are loaded yet — check data/words."
            ).setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(buildEmbed(words).build())
                .addActionRow(actionRow())
                .queue();
    }

    private Button[] actionRow() {
        return new Button[]{
                Button.primary(REGENERATE_BUTTON_ID, "🔄 Regenerate"),
                Button.secondary(NEW_SPEAKER_BUTTON_ID, "🗣️ New Speaker")
        };
    }

    /**
     * Builds the Discord card shown to the user.
     */
    public EmbedBuilder buildEmbed(List<String> words) {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setTitle("📖 Story Speaking Practice");

        StringBuilder description = new StringBuilder();
        description.append("Speak a short, connected story that naturally uses **all three** words below:\n\n");

        for (String word : words) {
            description.append("🔹 `").append(word).append("`\n");
        }

        embed.setDescription(description.toString());
        embed.setFooter("Tip: Decide the order and connect them with cause and effect, not just a list.");

        return embed;
    }

    private List<String> pickRandomWords() {
        List<String> pool = dataLoader.getWords();

        if (pool.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());

        int count = Math.min(WORD_COUNT, shuffled.size());
        return shuffled.subList(0, count);
    }
}