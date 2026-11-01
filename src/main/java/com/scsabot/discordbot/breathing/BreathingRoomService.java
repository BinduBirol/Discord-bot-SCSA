package com.scsabot.discordbot.breathing;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates the StamUnity Breathing Room.
 *
 * <p>Decisions are state based: every voice event re-counts the humans that are
 * actually in the room, so out-of-order event delivery cannot corrupt the
 * session. All public methods are synchronized; the work done under the lock is
 * non-blocking (JDA and LavaPlayer calls are asynchronous).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BreathingRoomService {

    /**
     * Voice-state events for our own disconnect can arrive late. If a brand new
     * session has just started, such a stale event must not tear it down.
     */
    private static final Duration STALE_BOT_DISCONNECT_WINDOW = Duration.ofSeconds(10);

    // The only field without an initializer, so it is the only constructor argument.
    private final BreathingRoomProperties properties;

    private final AudioPlayerManager playerManager = createPlayerManager();
    private final ScheduledExecutorService disconnectScheduler = createDisconnectScheduler();
    private final Map<Long, BreathingRoomSession> sessions = new ConcurrentHashMap<>();

    private static AudioPlayerManager createPlayerManager() {
        AudioPlayerManager manager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerLocalSource(manager);
        return manager;
    }

    private static ScheduledExecutorService createDisconnectScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "breathing-room-disconnect-timer");
            thread.setDaemon(true);
            return thread;
        });
    }

    // ------------------------------------------------------------------
    // Entry points used by BreathingRoomListener
    // ------------------------------------------------------------------

    /** A member joined the Breathing Room voice channel. */
    public synchronized void onMemberJoined(Guild guild, Member member) {
        if (member.getUser().isBot()) {
            return;
        }
        VoiceChannel channel = guild.getVoiceChannelById(properties.getVoiceChannelId());
        if (channel == null) {
            log.warn("Breathing Room voice channel {} was not found in guild {}.",
                    properties.getVoiceChannelId(), guild.getId());
            return;
        }
        evaluate(channel, member);
    }

    /** A member left the Breathing Room voice channel. */
    public synchronized void onMemberLeft(Guild guild) {
        VoiceChannel channel = guild.getVoiceChannelById(properties.getVoiceChannelId());
        if (channel == null) {
            return;
        }
        evaluate(channel, null);
    }

    /** The bot itself was removed from the Breathing Room channel (kicked or moved). */
    public synchronized void onBotDisconnected(Guild guild) {
        BreathingRoomSession session = sessions.get(guild.getIdLong());
        if (session == null) {
            return;
        }
        if (Duration.between(session.getStartedAt(), Instant.now()).compareTo(STALE_BOT_DISCONNECT_WINDOW) < 0) {
            return;
        }
        closeSession(session, "bot was disconnected from the Breathing Room channel");
    }

    /**
     * Picks up people who were already in the room before the bot (re)started,
     * since no join event will ever fire for them.
     */
    public synchronized void reconcile(JDA jda) {
        if (!properties.isEnabled()) {
            return;
        }
        VoiceChannel channel = jda.getVoiceChannelById(properties.getVoiceChannelId());
        if (channel == null) {
            log.warn("Breathing Room voice channel {} was not found; nothing to reconcile.",
                    properties.getVoiceChannelId());
            return;
        }
        evaluate(channel, null);
    }

    @PreDestroy
    public synchronized void shutdown() {
        for (BreathingRoomSession session : new ArrayList<>(sessions.values())) {
            closeSession(session, "application shutting down");
        }
        disconnectScheduler.shutdownNow();
        playerManager.shutdown();
    }

    // ------------------------------------------------------------------
    // Core logic
    // ------------------------------------------------------------------

    /**
     * Re-evaluates the room from its real current state.
     *
     * @param joined the member that just joined, or {@code null} when the trigger
     *               was a leave / startup reconciliation
     */
    private void evaluate(VoiceChannel channel, Member joined) {
        Guild guild = channel.getGuild();
        BreathingRoomSession session = sessions.get(guild.getIdLong());

        if (countHumans(channel) > 0) {
            if (session == null) {
                // First human: bot joins, intro plays, then meditation loops.
                startSession(channel);
                return;
            }

            if (session.cancelPendingDisconnect()) {
                log.info("Breathing Room (guild {}): someone returned, disconnect cancelled.", guild.getId());
            }
            if (joined != null) {
                sendWelcome(guild, joined);
            }
            return;
        }

        if (session != null) {
            scheduleDisconnect(session);
        }
    }

    private void startSession(VoiceChannel channel) {
        Guild guild = channel.getGuild();

        if (!Files.isRegularFile(properties.getMeditationFile())) {
            log.error("Breathing Room meditation file not found: {}", properties.getMeditationFile());
            return;
        }
        if (!guild.getSelfMember().hasPermission(
                channel, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_SPEAK)) {
            log.warn("Bot lacks View Channel / Connect / Speak in Breathing Room channel {}.", channel.getId());
            return;
        }

        BreathingRoomSession session = new BreathingRoomSession(
                guild.getJDA(),
                guild.getIdLong(),
                playerManager,
                properties.getInstructionsFile(),
                properties.getMeditationFile(),
                this::onSessionFailure
        );

        AudioManager audioManager = guild.getAudioManager();
        try {
            audioManager.setSendingHandler(session.getSendHandler());
            audioManager.setSelfDeafened(true);
            audioManager.openAudioConnection(channel);
        } catch (RuntimeException e) {
            log.error("Could not open Breathing Room audio connection.", e);
            session.close();
            audioManager.setSendingHandler(null);
            return;
        }

        sessions.put(guild.getIdLong(), session);
        log.info("Breathing Room (guild {}): first member present, session started.", guild.getId());
        session.start();
    }

    private void scheduleDisconnect(BreathingRoomSession session) {
        if (session.hasPendingDisconnect()) {
            return;
        }
        long delayMillis = properties.getDisconnectDelay().toMillis();
        ScheduledFuture<?> future = disconnectScheduler.schedule(
                () -> onDisconnectTimer(session),
                delayMillis,
                TimeUnit.MILLISECONDS
        );
        session.setPendingDisconnect(future);
        log.info("Breathing Room (guild {}): room empty, disconnecting in {}s unless someone returns.",
                session.getGuildId(), properties.getDisconnectDelay().toSeconds());
    }

    private synchronized void onDisconnectTimer(BreathingRoomSession session) {
        try {
            if (sessions.get(session.getGuildId()) != session) {
                return;
            }

            Guild guild = session.getJda().getGuildById(session.getGuildId());
            VoiceChannel channel = guild == null
                    ? null
                    : guild.getVoiceChannelById(properties.getVoiceChannelId());

            // Re-check at the last moment in case a join raced with this timer.
            if (channel != null && countHumans(channel) > 0) {
                session.cancelPendingDisconnect();
                return;
            }
            closeSession(session, "room stayed empty");
        } catch (RuntimeException e) {
            log.error("Breathing Room disconnect timer failed.", e);
        }
    }

    private synchronized void onSessionFailure(BreathingRoomSession session) {
        if (sessions.get(session.getGuildId()) == session) {
            closeSession(session, "audio failure");
        }
    }

    private void closeSession(BreathingRoomSession session, String reason) {
        sessions.remove(session.getGuildId(), session);
        session.close();

        try {
            Guild guild = session.getJda().getGuildById(session.getGuildId());
            if (guild != null) {
                AudioManager audioManager = guild.getAudioManager();
                audioManager.closeAudioConnection();
                audioManager.setSendingHandler(null);
            }
        } catch (RuntimeException e) {
            log.warn("Error while closing Breathing Room audio connection: {}", e.toString());
        }
        log.info("Breathing Room (guild {}): session closed ({}).", session.getGuildId(), reason);
    }

    private void sendWelcome(Guild guild, Member member) {
        GuildMessageChannel textChannel =
                guild.getChannelById(GuildMessageChannel.class, properties.getTextChannelId());
        if (textChannel == null) {
            log.warn("Breathing Room text channel {} was not found in guild {}.",
                    properties.getTextChannelId(), guild.getId());
            return;
        }
        if (!textChannel.canTalk()) {
            log.warn("Bot cannot send messages in Breathing Room text channel {}.", textChannel.getId());
            return;
        }

        String content = properties.formatWelcomeMessage(member.getAsMention());
        textChannel.sendMessage(content)
                .setAllowedMentions(EnumSet.of(Message.MentionType.USER))
                .queue(
                        success -> { },
                        error -> log.warn("Could not send Breathing Room welcome message: {}", error.toString())
                );
    }

    private static int countHumans(VoiceChannel channel) {
        return (int) channel.getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .count();
    }
}
