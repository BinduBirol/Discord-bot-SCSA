package com.scsabot.discordbot.story;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;

/**
 * /story image - Image speaking practice.
 * Pulls a random photo from the Pexels API (see PexelsImageService) and
 * challenges the user to describe it or tell a short story about it.
 * <p>
 * Same regenerate / new-speaker pattern as /story words:
 * - "🔄 Regenerate" edits the current message with a new image.
 * - "🗣️ New Speaker" posts a brand new message with its own image.
 */
@Component
public class StoryImageCommand extends ListenerAdapter {

    private static final String COMMAND_NAME = "story";
    private static final String SUBCOMMAND_NAME = "image";
    private static final String REGENERATE_BUTTON_ID = "story:image:regenerate";
    private static final String NEW_SPEAKER_BUTTON_ID = "story:image:newspeaker";
    private static final String FETCH_FAILED_MESSAGE =
            "❌ Couldn't fetch an image right now — try again in a moment.";

    private final PexelsImageService pexelsImageService;

    public StoryImageCommand(PexelsImageService pexelsImageService) {
        this.pexelsImageService = pexelsImageService;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(COMMAND_NAME)
                || !SUBCOMMAND_NAME.equals(event.getSubcommandName())) {
            return;
        }

        handleStoryCommand(event);
    }

    /**
     * Handles /story image when called directly by SlashCommandListener.
     */
    public void handleStoryCommand(SlashCommandInteractionEvent event) {

        PexelsImageService.PexelsImage image = pexelsImageService.fetchRandomImage();

        if (image == null) {
            event.reply(FETCH_FAILED_MESSAGE).setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(buildEmbed(image).build())
                .addActionRow(actionRow())
                .queue();
    }

    /**
     * "🔄 Regenerate" - same speaker, edit the existing message in place.
     */
    public void handleRegenerate(ButtonInteractionEvent event) {

        PexelsImageService.PexelsImage image = pexelsImageService.fetchRandomImage();

        if (image == null) {
            event.reply(FETCH_FAILED_MESSAGE).setEphemeral(true).queue();
            return;
        }

        event.editMessageEmbeds(buildEmbed(image).build())
                .setActionRow(actionRow())
                .queue();
    }

    /**
     * "🗣️ New Speaker" - post a brand new message with its own image,
     * leaving the previous message untouched.
     */
    public void handleNewSpeaker(ButtonInteractionEvent event) {

        PexelsImageService.PexelsImage image = pexelsImageService.fetchRandomImage();

        if (image == null) {
            event.reply(FETCH_FAILED_MESSAGE).setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(buildEmbed(image).build())
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
     * Builds the Discord card shown to the user. Includes photographer
     * credit and a link back to Pexels, as Pexels' API guidelines require.
     */
    public EmbedBuilder buildEmbed(PexelsImageService.PexelsImage image) {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setTitle("🖼️ Image Speaking Practice");

        String description = "Describe what you see, or tell a short story inspired by this image.";

        if (image.pexelsPageUrl() != null) {
            description += "\n\n📷 [Photo by " + image.photographer()
                    + " on Pexels](" + image.pexelsPageUrl() + ")";
        } else {
            description += "\n\n📷 Photo by " + image.photographer() + " on Pexels";
        }

        embed.setDescription(description);
        embed.setImage(image.imageUrl());
        embed.setFooter("Tip: mention what's happening, who's involved, and why it might be happening.");

        return embed;
    }
}