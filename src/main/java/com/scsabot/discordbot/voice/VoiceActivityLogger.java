package com.scsabot.discordbot.voice;

import com.scsabot.discordbot.discord.InviteTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.time.Instant;
import java.util.EnumSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceActivityLogger {

    private static final String VOICE_ACTIVITY_CHANNEL_ID = "1544618036047122442";

    private static final Color GREEN = new Color(46, 204, 113);
    private static final Color RED = new Color(231, 76, 60);
    private static final Color BLUE = new Color(52, 152, 219);
    private static final Color GRAY = new Color(149, 165, 166);

    private final ObjectProvider<JDA> jdaProvider;
    private final InviteTracker inviteTracker;

    private TextChannel getActivityChannel() {
        return jdaProvider.getObject()
                .getTextChannelById(VOICE_ACTIVITY_CHANNEL_ID);
    }

    /**
     * Sends the embed and guarantees no mention can ping anyone.
     */
    private void send(TextChannel channel, EmbedBuilder embed) {
        channel.sendMessageEmbeds(embed.build())
                .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                .queue();
    }

    public void logJoin(Member member, String channelId) {
        TextChannel channel = getActivityChannel();
        if (channel == null) return;

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(GREEN)
                .setTitle("Voice Activity")
                .setDescription(member.getAsMention() + " joined <#" + channelId + ">")
                .setTimestamp(Instant.now());

        send(channel, embed);
    }

    public void logLeave(Member member, String channelId, long durationSeconds) {
        TextChannel channel = getActivityChannel();
        if (channel == null) return;

        long minutes = durationSeconds / 60;
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(RED)
                .setTitle("Voice Activity")
                .setDescription(
                        member.getAsMention() + " left <#" + channelId + ">\n" +
                                "**Duration:** " + minutes + " minutes")
                .setTimestamp(Instant.now());

        send(channel, embed);
    }

    public void logMemberJoin(Member member) {
        TextChannel channel = getActivityChannel();
        if (channel == null) return;

        inviteTracker.resolveInvite(member.getGuild(), source -> {
            String invitedBy;
            if (source == null) {
                invitedBy = "Unknown (vanity URL, Discovery, or untracked)";
            } else {
                String inviter = source.inviterId() != null
                        ? "<@" + source.inviterId() + ">"
                        : "Unknown";
                invitedBy = inviter + " (`" + source.code() + "`)";
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(BLUE)
                    .setTitle("New Member")
                    .setDescription(member.getAsMention() + " joined the community.")
                    .addField("Invited by", invitedBy, false)
                    .setTimestamp(Instant.now());

            send(channel, embed);
        });
    }

    public void logMemberLeave(User user) {
        TextChannel channel = getActivityChannel();
        if (channel == null) return;

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(GRAY)
                .setTitle("Member Left")
                .setDescription(user.getAsMention() + " (" + user.getName() + ") left the community.")
                .setFooter("ID: " + user.getId())
                .setTimestamp(Instant.now());

        send(channel, embed);
    }

    public void sendLeaveDm(User user, String guildName) {
        if (user.isBot()) return;

        String message = "We're sorry to see you leave " + guildName + ". "
                + "You're always welcome back!\n\n"
                + "If you have any complaints, you can always let us know how we can improve our community.";

        user.openPrivateChannel()
                .flatMap(ch -> ch.sendMessage(message))
                .queue(
                        ok -> log.info("Sent leave DM to {}", user.getId()),
                        err -> log.warn("Could not DM {}: {}", user.getId(), err.getMessage())
                );
    }

    public void logBotOnline() {
        TextChannel channel = getActivityChannel();
        if (channel == null) return;

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(GREEN)
                .setTitle("StamUnity bot is Online")
                .setDescription("The bot is online and ready to help keep the community safe and friendly.")
                .setTimestamp(Instant.now())
                .setFooter("StamUnity bot");

        send(channel, embed);
    }

    public void logBotOffline() {
        TextChannel channel = getActivityChannel();
        if (channel == null) return;

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(RED)
                .setTitle("StamUnity bot is Offline")
                .setDescription("Some automated features may be temporarily unavailable.")
                .setTimestamp(Instant.now())
                .setFooter("StamUnity bot");

        send(channel, embed);
    }
}