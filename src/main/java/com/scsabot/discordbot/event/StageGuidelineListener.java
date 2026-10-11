package com.scsabot.discordbot.event;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceRequestToSpeakEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StageGuidelineListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(StageGuidelineListener.class);

    // cooldown so rejoining doesn't spam the welcome message
    private static final Duration COOLDOWN = Duration.ofMinutes(5);

    private final long stageChannelId;
    private final long eventChatId;
    private final Map<Long, Instant> lastSent = new ConcurrentHashMap<>();

    public StageGuidelineListener(@Value("${discord.stage-channel-id}") long stageChannelId,
                                  @Value("${discord.event-chat-channel-id}") long eventChatId) {
        this.stageChannelId = stageChannelId;
        this.eventChatId = eventChatId;
        log.info("StageGuidelineListener created");
    }

    // 1) Someone joins the stage -> post welcome + guidelines in event chat
    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getChannelJoined() == null || event.getChannelJoined().getIdLong() != stageChannelId) return;

        Member member = event.getMember();
        if (member.getUser().isBot()) return;

        Instant last = lastSent.get(member.getIdLong());
        if (last != null && Duration.between(last, Instant.now()).compareTo(COOLDOWN) < 0) return;
        lastSent.put(member.getIdLong(), Instant.now());

        TextChannel chat = event.getJDA().getTextChannelById(eventChatId);
        if (chat == null) {
            log.warn("Event chat channel {} not found or not a text channel", eventChatId);
            return;
        }

        String stageUrl = "https://discord.com/channels/" + event.getGuild().getId() + "/" + stageChannelId;

        chat.sendMessage(member.getAsMention() + " joined!")
                .addEmbeds(buildGuidelines(member))
                .addActionRow(Button.link(stageUrl, "🎪 Go to the Stage"))
                .queue(
                        ok -> {
                        },
                        err -> log.error("Failed to post stage guidelines", err)
                );
    }

    // 2) Someone raises their hand -> alert in event chat
    @Override
    public void onGuildVoiceRequestToSpeak(GuildVoiceRequestToSpeakEvent event) {
        var channel = event.getVoiceState().getChannel();
        if (channel == null || channel.getIdLong() != stageChannelId) return;
        if (event.getNewTime() == null) return; // hand lowered

        TextChannel chat = event.getJDA().getTextChannelById(eventChatId);
        if (chat == null) return;

        String stageUrl = "https://discord.com/channels/" + event.getGuild().getId() + "/" + stageChannelId;

        chat.sendMessage("✋ " + event.getMember().getAsMention() + " wants to speak.")
                .addActionRow(Button.link(stageUrl, "🎪 Go to the Stage"))
                .queue(
                        ok -> {
                        },
                        err -> log.error("Failed to post raise-hand alert", err)
                );
    }

    private MessageEmbed buildGuidelines(Member member) {
        return new EmbedBuilder()
                .setAuthor(member.getEffectiveName(), null, member.getEffectiveAvatarUrl())
                .setTitle("🎉 Welcome to the Stage! 🎙️")
                .setDescription("We're glad you're here! You've joined as an **audience member**.")
                .addField("✋ Want to speak?",
                        "Click **Request to Speak** and wait for your turn. "
                                + "A moderator will invite you on stage.", false)
                .setColor(new Color(0xFFB800))
                .build();
    }
}