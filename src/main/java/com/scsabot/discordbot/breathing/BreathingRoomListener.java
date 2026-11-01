package com.scsabot.discordbot.breathing;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Listens for voice-state changes in the Breathing Room channel.
 *
 * <p>This listener registers itself with JDA once the application is ready, so
 * {@code JdaConfig} does not need to change. It is independent of
 * {@code VoiceStateListener}; both receive the same events.</p>
 *
 * <p>The constructor is written out (instead of {@code @RequiredArgsConstructor})
 * because Lombok does not copy {@code @Qualifier} onto constructor parameters
 * unless {@code lombok.config} is changed.</p>
 */
@Slf4j
@Component
public class BreathingRoomListener extends ListenerAdapter {

    private final BreathingRoomService breathingRoomService;
    private final BreathingRoomProperties properties;
    private final ObjectProvider<JDA> jdaProvider;
    private final TaskExecutor discordEventExecutor;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    public BreathingRoomListener(
            BreathingRoomService breathingRoomService,
            BreathingRoomProperties properties,
            ObjectProvider<JDA> jdaProvider,
            @Qualifier("discordEventExecutor") TaskExecutor discordEventExecutor) {
        this.breathingRoomService = breathingRoomService;
        this.properties = properties;
        this.jdaProvider = jdaProvider;
        this.discordEventExecutor = discordEventExecutor;
    }

    /** Attaches this listener to the existing JDA bean after startup. */
    @EventListener(ApplicationReadyEvent.class)
    public void registerWithJda() {
        if (!properties.isEnabled()) {
            return;
        }
        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("JDA is not available (no bot token?); Breathing Room will not start.");
            return;
        }
        if (!registered.compareAndSet(false, true)) {
            return;
        }

        jda.addEventListener(this);
        log.info("Breathing Room listener registered.");

        // Handle people already sitting in the room when the bot starts.
        discordEventExecutor.execute(() -> {
            try {
                breathingRoomService.reconcile(jda);
            } catch (RuntimeException e) {
                log.error("Breathing Room startup reconciliation failed.", e);
            }
        });
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (!properties.isEnabled()) {
            return;
        }

        long roomId = properties.getVoiceChannelId();
        AudioChannelUnion joined = event.getChannelJoined();
        AudioChannelUnion left = event.getChannelLeft();

        boolean joinedRoom = joined != null && joined.getIdLong() == roomId;
        boolean leftRoom = left != null && left.getIdLong() == roomId;
        if (!joinedRoom && !leftRoom) {
            return;
        }

        Member member = event.getMember();
        boolean isSelf = member.getUser().getIdLong() == event.getJDA().getSelfUser().getIdLong();

        discordEventExecutor.execute(() -> {
            try {
                if (isSelf) {
                    if (leftRoom && !joinedRoom) {
                        breathingRoomService.onBotDisconnected(event.getGuild());
                    }
                    return;
                }
                if (joinedRoom) {
                    breathingRoomService.onMemberJoined(event.getGuild(), member);
                } else {
                    breathingRoomService.onMemberLeft(event.getGuild());
                }
            } catch (RuntimeException e) {
                log.error("Breathing Room failed to handle voice update for member {}.", member.getId(), e);
            }
        });
    }
}
