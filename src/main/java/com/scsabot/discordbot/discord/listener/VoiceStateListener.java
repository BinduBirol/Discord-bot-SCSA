package com.scsabot.discordbot.discord.listener;

import com.scsabot.discordbot.entity.DiscordMember;
import com.scsabot.discordbot.entity.VoiceSession;
import com.scsabot.discordbot.service.MemberService;
import com.scsabot.discordbot.service.VoiceSessionService;
import com.scsabot.discordbot.voice.VoiceActivityLogger;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class VoiceStateListener extends ListenerAdapter {

    private static final Logger log =
            LoggerFactory.getLogger(VoiceStateListener.class);

    private final MemberService memberService;
    private final VoiceSessionService voiceSessionService;
    private final VoiceActivityLogger voiceActivityLogger;
    private final TaskExecutor discordEventExecutor;

    public VoiceStateListener(
            MemberService memberService,
            VoiceSessionService voiceSessionService,
            VoiceActivityLogger voiceActivityLogger,
            TaskExecutor discordEventExecutor) {

        this.memberService = memberService;
        this.voiceSessionService = voiceSessionService;
        this.voiceActivityLogger = voiceActivityLogger;
        this.discordEventExecutor = discordEventExecutor;
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {

        // Member joined a voice channel
        if (event.getChannelLeft() == null
                && event.getChannelJoined() != null) {

            discordEventExecutor.execute(
                    () -> handleVoiceJoin(event)
            );

            return;
        }

        // Member left a voice channel
        if (event.getChannelLeft() != null
                && event.getChannelJoined() == null) {

            discordEventExecutor.execute(
                    () -> handleVoiceLeave(event)
            );

            return;
        }

        // Member moved from one voice channel to another
        if (event.getChannelLeft() != null
                && event.getChannelJoined() != null) {

            discordEventExecutor.execute(
                    () -> handleVoiceMove(event)
            );
        }
    }

    private void handleVoiceJoin(GuildVoiceUpdateEvent event) {

        String guildId = event.getGuild().getId();
        String userId = event.getMember().getId();
        String channelId = event.getChannelJoined().getId();
        String channelName = event.getChannelJoined().getName();

        DiscordMember member =
                memberService
                        .findByDiscordUserIdAndGuildId(guildId, userId)
                        .orElseGet(() ->
                                memberService.upsertMember(
                                        guildId,
                                        userId,
                                        event.getMember().getUser().getName(),
                                        event.getMember().getEffectiveName()
                                )
                        );

        voiceSessionService.startSession(
                member,
                guildId,
                channelId
        );

        // Send message to #📊・voice-activity
        voiceActivityLogger.logJoin(
                event.getMember().getEffectiveName(),
                channelName
        );

        log.info(
                "Member {} joined voice channel {}",
                userId,
                channelId
        );
    }

    private void handleVoiceLeave(GuildVoiceUpdateEvent event) {

        String guildId = event.getGuild().getId();
        String userId = event.getMember().getId();

        memberService
                .findByDiscordUserIdAndGuildId(guildId, userId)
                .ifPresent(member -> {

                    Optional<VoiceSession> result =
                            voiceSessionService.endSession(member);

                    result.ifPresent(session -> {

                        String channelName = "Unknown";

                        var channel = event.getGuild()
                                .getVoiceChannelById(
                                        session.getVoiceChannelId()
                                );

                        if (channel != null) {
                            channelName = channel.getName();
                        }

                        // Send message to #📊・voice-activity
                        voiceActivityLogger.logLeave(
                                event.getMember().getEffectiveName(),
                                channelName,
                                session.getDurationSeconds()
                        );

                        log.info(
                                "Member {} left voice channel {} after {} seconds",
                                userId,
                                session.getVoiceChannelId(),
                                session.getDurationSeconds()
                        );
                    });
                });
    }

    private void handleVoiceMove(GuildVoiceUpdateEvent event) {

        String guildId = event.getGuild().getId();
        String userId = event.getMember().getId();

        memberService
                .findByDiscordUserIdAndGuildId(guildId, userId)
                .ifPresent(member -> {

                    // Close the old session
                    Optional<VoiceSession> result =
                            voiceSessionService.endSession(member);

                    result.ifPresent(session -> {

                        String oldChannelName = "Unknown";

                        var oldChannel = event.getGuild()
                                .getVoiceChannelById(
                                        session.getVoiceChannelId()
                                );

                        if (oldChannel != null) {
                            oldChannelName = oldChannel.getName();
                        }

                        voiceActivityLogger.logLeave(
                                event.getMember().getEffectiveName(),
                                oldChannelName,
                                session.getDurationSeconds()
                        );

                        log.info(
                                "Member {} moved from voice channel {} after {} seconds",
                                userId,
                                session.getVoiceChannelId(),
                                session.getDurationSeconds()
                        );
                    });

                    // Start the new session
                    String newChannelId =
                            event.getChannelJoined().getId();

                    String newChannelName =
                            event.getChannelJoined().getName();

                    voiceSessionService.startSession(
                            member,
                            guildId,
                            newChannelId
                    );

                    voiceActivityLogger.logJoin(
                            event.getMember().getEffectiveName(),
                            newChannelName
                    );

                    log.info(
                            "Member {} joined new voice channel {}",
                            userId,
                            newChannelId
                    );
                });
    }
}