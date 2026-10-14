package com.scsabot.discordbot.service;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import net.dv8tion.jda.api.entities.Member;

@Service
public class WelcomeService {

    private static final long GUIDELINES_THREAD_ID = 1559817030511890472L;

    private final ObjectProvider<JDA> jdaProvider;

    public WelcomeService(ObjectProvider<JDA> jdaProvider) {
        this.jdaProvider = jdaProvider;
    }

    public void welcomeMember(Member member) {

        JDA jda = jdaProvider.getObject();

        ThreadChannel channel =
                jda.getThreadChannelById(GUIDELINES_THREAD_ID);

        if (channel == null) {
            System.out.println(
                    "❌ Welcome thread not found: " + GUIDELINES_THREAD_ID
            );
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();

        embed.setTitle(member.getEffectiveName() + ", Welcome! ❤️");

        embed.setDescription(
                " This is a friendly, judgment-free community for people who stutter or stammer, anyone working on their speaking skills, and those who want to support them.\n\n"
                        + "📜 **First, start with:**\n"
                        + "https://discord.com/channels/1543886876786565140/1559847169354235954\n\n"
                        + "🌍 **Then:**\n"
                        + "https://discord.com/channels/1543886876786565140/1559155325385576541\n"
                        + "https://discord.com/channels/1543886876786565140/1559526392889221120"
        );


        embed.setFooter("No pressure to speak. Take your time and enjoy being part of StamUnity! 🤗");

        channel.sendMessage(member.getAsMention())
                .setEmbeds(embed.build())
                .queue(
                        success -> System.out.println(
                                "✅ Welcome message sent to " + member.getEffectiveName()
                        ),
                        error -> System.out.println(
                                "❌ Failed to send welcome message: " + error.getMessage()
                        )
                );
    }


}