package com.scsabot.discordbot.speakers;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.stereotype.Component;

/** Handles /speaker list. Call handle(...) from your SlashCommandListener switch. */
@Component
public class SpeakerCommand {

    private final SpeakerListService service;

    public SpeakerCommand(SpeakerListService service) {
        this.service = service;
    }

    public void handle(SlashCommandInteractionEvent event) {
        if (!"list".equals(event.getSubcommandName())) {
            event.reply("Unknown speaker command.").setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply("This command only works in the server.").setEphemeral(true).queue();
            return;
        }

        GuildVoiceState voiceState = member.getVoiceState();
        AudioChannel channel = voiceState != null ? voiceState.getChannel() : null;
        if (channel == null) {
            event.reply("Join a voice channel first, then run `/speaker list` again.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        SpeakerSession session = service.createSession(channel, member.getIdLong());

        event.replyEmbeds(service.buildEmbed(session))
                .setComponents(service.buildComponents())
                .flatMap(InteractionHook::retrieveOriginal)
                .queue(message -> service.register(message.getIdLong(), session));
    }
}
