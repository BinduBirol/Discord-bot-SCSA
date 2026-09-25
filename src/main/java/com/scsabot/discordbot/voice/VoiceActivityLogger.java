package com.scsabot.discordbot.voice;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.time.Instant;

@Service
public class VoiceActivityLogger {

    private static final String VOICE_ACTIVITY_CHANNEL_ID = "1544618036047122442";

    private static final Color GREEN = new Color(46, 204, 113);
    private static final Color RED = new Color(231, 76, 60);
    private static final Color BLUE = new Color(52, 152, 219);
    private static final Color GRAY = new Color(149, 165, 166);

    private final ObjectProvider<JDA> jdaProvider;

    public VoiceActivityLogger(ObjectProvider<JDA> jdaProvider) {
        this.jdaProvider = jdaProvider;
    }

    private TextChannel getActivityChannel() {
        return jdaProvider.getObject()
                .getTextChannelById(VOICE_ACTIVITY_CHANNEL_ID);
    }

    public void logJoin(String username, String channelName) {

        TextChannel channel = getActivityChannel();

        if (channel == null) {
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(GREEN)
                .setTitle("Voice Activity")
                .setDescription(
                        "**" + username + "** joined **" + channelName + "**"
                )
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    public void logLeave(
            String username,
            String channelName,
            long durationSeconds) {

        TextChannel channel = getActivityChannel();

        if (channel == null) {
            return;
        }

        long minutes = durationSeconds / 60;
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(RED)
                .setTitle("Voice Activity")
                .setDescription(
                        "**" + username + "** left **" + channelName + "**\n" +
                                "**Duration:** " + minutes + " minutes"
                )
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    public void logMemberJoin(String username) {

        TextChannel channel = getActivityChannel();

        if (channel == null) {
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(BLUE)
                .setTitle("New Member")
                .setDescription(
                        "**" + username + "** joined the community."
                )
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    public void logMemberLeave(String username) {

        TextChannel channel = getActivityChannel();

        if (channel == null) {
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(GRAY)
                .setTitle("Member Left")
                .setDescription(
                        "**" + username + "** left the community."
                )
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    public void logBotOnline() {

        TextChannel channel = getActivityChannel();

        if (channel == null) {
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(GREEN)
                .setTitle("StamUnity bot is Online")
                .setDescription(
                        "The bot is online and ready to help keep the community safe and friendly."
                )
                .setTimestamp(Instant.now())
                .setFooter("StamUnity bot");

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    public void logBotOffline() {

        TextChannel channel = getActivityChannel();

        if (channel == null) {
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(RED)
                .setTitle("StamUnity bot is Offline")
                .setDescription(
                        "Some automated features may be temporarily unavailable."
                )
                .setTimestamp(Instant.now())
                .setFooter("StamUnity bot");

        channel.sendMessageEmbeds(embed.build()).queue();
    }
}