package com.scsabot.discordbot.config;

import com.scsabot.discordbot.discord.command.SlashCommandListener;
import com.scsabot.discordbot.discord.listener.GuildMemberEventListener;
import com.scsabot.discordbot.discord.listener.VoiceStateListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumSet;

@Configuration
public class JdaConfig {

    private static final Logger log = LoggerFactory.getLogger(JdaConfig.class);

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "discord.bot-token")
    public JDA jda(
            DiscordProperties properties,
            GuildMemberEventListener guildMemberEventListener,
            VoiceStateListener voiceStateListener,
            SlashCommandListener slashCommandListener
    ) throws InterruptedException {

        String token = properties.getBotToken();

        if (token == null || token.isBlank()) {
            log.warn("Discord bot token is not configured. JDA will not be started.");
            return null;
        }

        EnumSet<GatewayIntent> intents = EnumSet.of(
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.DIRECT_MESSAGES,
                GatewayIntent.GUILD_VOICE_STATES,
                GatewayIntent.MESSAGE_CONTENT
        );

        JDA jda = JDABuilder.createDefault(token, intents)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setActivity(Activity.watching("the community"))
                .addEventListeners(
                        guildMemberEventListener,
                        voiceStateListener,
                        slashCommandListener
                )
                .build()
                .awaitReady();

        log.info("Discord JDA connected as {}", jda.getSelfUser().getAsTag());

        return jda;
    }
}