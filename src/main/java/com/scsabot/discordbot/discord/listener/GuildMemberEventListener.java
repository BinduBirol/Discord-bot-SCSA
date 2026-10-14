package com.scsabot.discordbot.discord.listener;

import com.scsabot.discordbot.service.WelcomeService;
import com.scsabot.discordbot.voice.VoiceActivityLogger;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class GuildMemberEventListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(GuildMemberEventListener.class);

    private final VoiceActivityLogger voiceActivityLogger;
    private final WelcomeService welcomeService;
    private final TaskExecutor discordEventExecutor;

    private static final long WELCOME_THREAD_ID = 1559817030511890472L;

    public GuildMemberEventListener(
            VoiceActivityLogger voiceActivityLogger,
            WelcomeService welcomeService,
            @Qualifier("discordEventExecutor") TaskExecutor discordEventExecutor) {
        this.voiceActivityLogger = voiceActivityLogger;
        this.welcomeService = welcomeService;
        this.discordEventExecutor = discordEventExecutor;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Member member = event.getMember();
        log.info("Guild member joined: {} ({})",
                member.getEffectiveName(), member.getIdLong());

        discordEventExecutor.execute(() -> {
            voiceActivityLogger.logMemberJoin(member.getEffectiveName());
            welcomeService.welcomeMember(member);

            var thread = event.getGuild().getThreadChannelById(WELCOME_THREAD_ID);

            if (thread == null) {
                log.warn("Welcome thread not found: {}", WELCOME_THREAD_ID);
                return;
            }
            /*
            thread.sendMessage(
                    "👋 Hi, " + member.getAsMention() + " welcome!\n"
                            + " Check the https://discord.com/channels/1543886876786565140/1559847169354235954 and then\n" +
                            "https://discord.com/channels/1543886876786565140/1559155325385576541"
                            + "https://discord.com/channels/1543886876786565140/1559526392889221120"

            ).queue(
                    success -> log.info("Welcome message sent in thread for {}",
                            member.getEffectiveName()),
                    error -> log.error("Failed to send welcome message in thread", error)
            );
            */

        });
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        log.info("Guild member left: {} ({})", event.getUser().getName(), event.getUser().getId());
        discordEventExecutor.execute(() -> voiceActivityLogger.logMemberLeave(event.getUser().getName()));
    }
}
