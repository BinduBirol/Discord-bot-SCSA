package com.scsabot.discordbot.voice;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class VoiceActivityLogger {

    private static final String VOICE_ACTIVITY_CHANNEL_ID = "1544618036047122442";

    private final ObjectProvider<JDA> jdaProvider;

    public VoiceActivityLogger(ObjectProvider<JDA> jdaProvider) {
        this.jdaProvider = jdaProvider;
    }

    public void logJoin(String username, String channelName) {

        JDA jda = jdaProvider.getObject();

        TextChannel channel =
                jda.getTextChannelById(VOICE_ACTIVITY_CHANNEL_ID);

        if (channel == null) {
            return;
        }

        channel.sendMessage(
                "🟢 **" + username + "** joined **" + channelName + "**"
        ).queue();
    }

    public void logLeave(
            String username,
            String channelName,
            long durationSeconds) {

        JDA jda = jdaProvider.getObject();

        TextChannel channel =
                jda.getTextChannelById(VOICE_ACTIVITY_CHANNEL_ID);

        if (channel == null) {
            return;
        }

        long minutes = durationSeconds / 60;

        channel.sendMessage(
                "🔴 **" + username + "** left **" + channelName +
                        "** — ⏱️ " + minutes + " minutes"
        ).queue();
    }


    public void logMemberJoin(String username) {

        JDA jda = jdaProvider.getObject();

        TextChannel channel =
                jda.getTextChannelById(VOICE_ACTIVITY_CHANNEL_ID);

        if (channel == null) {
            return;
        }

        channel.sendMessage(
                "👋 **" + username + "** joined the server!"
        ).queue();
    }

    public void logMemberLeave(String username) {

        JDA jda = jdaProvider.getObject();

        TextChannel channel =
                jda.getTextChannelById(VOICE_ACTIVITY_CHANNEL_ID);

        if (channel == null) {
            return;
        }

        channel.sendMessage(
                "👋 **" + username + "** left the server."
        ).queue();
    }


    public void logBotOnline() {

        JDA jda = jdaProvider.getObject();

        TextChannel channel =
                jda.getTextChannelById(VOICE_ACTIVITY_CHANNEL_ID);

        if (channel == null) {
            return;
        }

        channel.sendMessage(
                """
                        🟢 **Hey everyone! I’m online! 🤖👋**
                        
                        I’ll be here keeping an eye on the server, recording voice-practice activity, and helping keep our community safe and friendly.
                        
                        🎤 **Practice your speech, join the conversations, and support each other!**
                        📊 Your voice activity may be recorded for practice tracking and community stats.
                        📜 And please remember — **follow the rules and be respectful to everyone.**
                        
                        Let’s make this a safe place where nobody has to worry about being judged. ❤️
                        
                        — **SCSA ModBot 🤖**
                        """
        ).queue();
    }

    public void logBotOffline() {

        JDA jda = jdaProvider.getObject();

        TextChannel channel =
                jda.getTextChannelById(VOICE_ACTIVITY_CHANNEL_ID);

        if (channel == null) {
            return;
        }

        channel.sendMessage(
                "🔴 **SCSA ModBot is offline.**"
        ).queue();
    }
}