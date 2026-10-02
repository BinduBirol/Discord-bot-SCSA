package com.scsabot.discordbot.timer;

import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * /timer minutes [label] - starts a countdown in the channel.
 * Shows a live embed with a progress bar, pings the user when time is up,
 * and offers Cancel and +1 min buttons.
 * <p>
 * Other components (e.g. Table Topics) can start a timer from a button
 * via {@link #startFromButton(ButtonInteractionEvent, int, String)}.
 */
@Component
public class TimerCommand {

    public static final String CANCEL_PREFIX = "timer:cancel:";
    public static final String ADD_PREFIX = "timer:add:";

    private static final int MAX_MINUTES = 60;
    private static final int ADD_SECONDS = 60;
    private static final int UPDATE_SECONDS = 10;
    private static final int BAR_SEGMENTS = 16;

    private static final Color GREEN = new Color(0x57F287);
    private static final Color YELLOW = new Color(0xFEE75C);
    private static final Color RED = new Color(0xED4245);
    private static final Color GREY = new Color(0x95A5A6);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, TimerState> active = new ConcurrentHashMap<>();

    private static final class TimerState {
        final String id;
        final String mention;
        final String label;
        final MessageChannel channel;
        final AtomicReference<Message> message = new AtomicReference<>();
        volatile long totalSeconds;
        volatile long endEpoch;
        volatile ScheduledFuture<?> end;
        volatile ScheduledFuture<?> ticker;

        TimerState(String id, String mention, String label, MessageChannel channel,
                   long totalSeconds, long endEpoch) {
            this.id = id;
            this.mention = mention;
            this.label = label;
            this.channel = channel;
            this.totalSeconds = totalSeconds;
            this.endEpoch = endEpoch;
        }

        long secondsLeft() {
            return Math.max(0, endEpoch - Instant.now().getEpochSecond());
        }

        void cancelTasks() {
            ScheduledFuture<?> e = end;
            ScheduledFuture<?> t = ticker;
            if (e != null) e.cancel(false);
            if (t != null) t.cancel(false);
        }
    }

    // ------------------------------------------------------------------
    // Entry points
    // ------------------------------------------------------------------

    public void handleTimer(SlashCommandInteractionEvent event) {

        int minutes = event.getOption("minutes", 1, OptionMapping::getAsInt);
        String label = event.getOption("label", "Time's up!", OptionMapping::getAsString);

        startTimer(event, minutes, label);
    }

    /**
     * Starts a timer from a button click and posts it as a new message.
     */
    public void startFromButton(ButtonInteractionEvent event, int minutes, String label) {
        startTimer(event, minutes, label);
    }

    // ------------------------------------------------------------------
    // Start / finish
    // ------------------------------------------------------------------

    private void startTimer(IReplyCallback event, int minutes, String label) {

        if (minutes < 1 || minutes > MAX_MINUTES) {
            event.reply("❌ Pick a time between 1 and " + MAX_MINUTES + " minutes.")
                    .setEphemeral(true).queue();
            return;
        }

        long seconds = minutes * 60L;
        String id = UUID.randomUUID().toString();

        TimerState state = new TimerState(
                id,
                event.getUser().getAsMention(),
                label,
                event.getMessageChannel(),
                seconds,
                Instant.now().getEpochSecond() + seconds
        );

        state.ticker = scheduler.scheduleAtFixedRate(
                () -> refresh(state), UPDATE_SECONDS, UPDATE_SECONDS, TimeUnit.SECONDS);
        scheduleEnd(state);
        active.put(id, state);

        event.replyEmbeds(buildEmbed(state))
                .addActionRow(buttons(id))
                .queue(hook -> hook.retrieveOriginal().queue(state.message::set));
    }

    private void scheduleEnd(TimerState state) {
        state.end = scheduler.schedule(
                () -> finish(state), state.secondsLeft(), TimeUnit.SECONDS);
    }

    private void refresh(TimerState state) {
        Message msg = state.message.get();
        if (msg == null || state.secondsLeft() <= 0) {
            return;
        }
        msg.editMessageEmbeds(buildEmbed(state)).queue(null, err -> {
        });
    }

    private void finish(TimerState state) {
        if (active.remove(state.id) == null) {
            return;
        }
        ScheduledFuture<?> t = state.ticker;
        if (t != null) t.cancel(false);

        Message msg = state.message.get();
        if (msg != null) {
            MessageEmbed done = new EmbedBuilder()
                    .setColor(GREY)
                    .setTitle("✅ Timer ended")
                    .build();
            msg.editMessageEmbeds(done).setComponents().queue(null, err -> {
            });
        }

        state.channel.sendMessage("⏰ " + state.mention + " " + state.label).queue();
    }

    // ------------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------------

    public void handleCancel(ButtonInteractionEvent event) {

        String id = event.getComponentId().substring(CANCEL_PREFIX.length());
        TimerState state = active.remove(id);

        if (state == null) {
            event.reply("That timer already finished.").setEphemeral(true).queue();
            return;
        }

        state.cancelTasks();

        MessageEmbed cancelled = new EmbedBuilder()
                .setColor(GREY)
                .setTitle("🛑 Timer cancelled")
                .setDescription("Cancelled by " + event.getUser().getAsMention())
                .build();

        event.editMessageEmbeds(cancelled).setComponents().queue();
    }

    public void handleAddTime(ButtonInteractionEvent event) {

        String id = event.getComponentId().substring(ADD_PREFIX.length());
        TimerState state = active.get(id);

        if (state == null) {
            event.reply("That timer already finished.").setEphemeral(true).queue();
            return;
        }

        synchronized (state) {
            if (state.secondsLeft() + ADD_SECONDS > MAX_MINUTES * 60L) {
                event.reply("❌ A timer can't run longer than " + MAX_MINUTES + " minutes.")
                        .setEphemeral(true).queue();
                return;
            }

            ScheduledFuture<?> oldEnd = state.end;
            if (oldEnd != null) oldEnd.cancel(false);

            state.endEpoch += ADD_SECONDS;
            state.totalSeconds += ADD_SECONDS;
            scheduleEnd(state);
        }

        event.editMessageEmbeds(buildEmbed(state))
                .setActionRow(buttons(id))
                .queue();
    }

    private List<Button> buttons(String id) {
        return List.of(
                Button.secondary(ADD_PREFIX + id, "➕ 1 min"),
                Button.danger(CANCEL_PREFIX + id, "🛑 Cancel")
        );
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private MessageEmbed buildEmbed(TimerState state) {

        long left = state.secondsLeft();
        long total = Math.max(1, state.totalSeconds);
        double remaining = Math.min(1.0, (double) left / total);
        double elapsed = 1.0 - remaining;

        Color color = remaining > 0.5 ? GREEN : remaining > 0.2 ? YELLOW : RED;

        String description =
                "## ⏳ " + formatLeft(left) + "\n"
                        + progressBar(elapsed) + " **" + Math.round(elapsed * 100) + "%**\n\n"
                        + "👤 " + state.mention + " • Ends at <t:" + state.endEpoch + ":T>";

        return new EmbedBuilder()
                .setColor(color)
                .setTitle("⏱️ " + (total / 60) + " min timer")
                .setDescription(description)
                .setFooter("Updates every " + UPDATE_SECONDS + "s")
                .build();
    }

    private String progressBar(double fraction) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * BAR_SEGMENTS);
        return "▰".repeat(filled) + "▱".repeat(BAR_SEGMENTS - filled);
    }

    private String formatLeft(long totalSeconds) {
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}