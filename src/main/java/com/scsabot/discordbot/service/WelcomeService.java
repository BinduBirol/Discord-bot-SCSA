package com.scsabot.discordbot.service;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import net.dv8tion.jda.api.entities.Member;

@Service
public class WelcomeService {

    private static final String WELCOME_CHANNEL_ID = "1543886877512175710";
    private static final String PRACTICE_GUIDE_CHANNEL_ID = "1552920324591321190";

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

        EmbedBuilder embed = new EmbedBuilder();

        embed.setTitle("👋 Welcome to StamUnity! ❤️");


        embed.setDescription(
                """
                        Welcome, %s!
                        
                        We’re really happy to have you here! This is a friendly, judgment-free community for people who stutter or stammer.
                        
                        Take your time, speak freely, and most importantly — **don't be afraid to stutter.** 🫶
                        
                        📜 **Before you get started**
                        Please take a moment to check out our [server rules](https://discord.com/channels/1543886876786565140/1556929839993331732).
                        
                        📚 **Want to practice?**
                        We focus on both structured practice and casual chatting here. Whenever you feel like your speech is breaking up more, you can always go back to structured practice.
                        Check out <#1552920324591321190> for our structured practice guide.
                        
                        🗣️ **Speech coaches & supporters are welcome!**
                        We’re happy to have speech coaches, professionals, and supporters in the community. Please participate as a community member, not as an expert or authority.
                        
                        """.formatted(member.getAsMention())
        );


        embed.setFooter("Welcome to the community! 🤗");

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> System.out.println(
                        "✅ Welcome message sent to " + member.getEffectiveName()
                ),
                error -> System.out.println(
                        "❌ Failed to send welcome message: " + error.getMessage()
                )
        );
    }
}