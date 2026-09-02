package com.scsabot.discordbot.service;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import net.dv8tion.jda.api.entities.Member;

@Service
public class WelcomeService {

    private static final String WELCOME_CHANNEL_ID = "1543886877512175710";

    private final ObjectProvider<JDA> jdaProvider;

    public WelcomeService(ObjectProvider<JDA> jdaProvider) {
        this.jdaProvider = jdaProvider;
    }

    public void welcomeMember(Member member) {

        JDA jda = jdaProvider.getObject();

        TextChannel channel =
                jda.getTextChannelById(WELCOME_CHANNEL_ID);

        if (channel == null) {
            System.out.println(
                    "❌ Welcome channel not found: " + WELCOME_CHANNEL_ID
            );
            return;
        }

        channel.sendMessage(
                """
                👋 **Welcome to SCSA, %s!** ❤️
    
                We’re really happy to have you here! This is a friendly, judgment-free community for people who stutter or stammer.
    
                Before you jump in:
    
                👤 **Introduce yourself**
                🌍 **Tell us where you're from**
                🗣️ **Share your stuttering level**
                📜 **Read the rules**
                🎤 **Join a voice room and practice when you're ready!**
    
                Take your time, speak freely, and most importantly — **don't be afraid to stutter.** 🫶
    
                Welcome to the community! 🤗
                """.formatted(member.getAsMention())
        ).queue(
                success -> System.out.println(
                        "✅ Welcome message sent to " + member.getEffectiveName()
                ),
                error -> System.out.println(
                        "❌ Failed to send welcome message: " + error.getMessage()
                )
        );
    }
}