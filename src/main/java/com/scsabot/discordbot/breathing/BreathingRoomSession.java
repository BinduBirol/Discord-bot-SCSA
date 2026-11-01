package com.scsabot.discordbot.breathing;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * One Breathing Room session for one guild.
 *
 * <p>Lifecycle: load both audio files, play the instruction audio once, then
 * loop the meditation audio until {@link #close()} is called. A new session
 * always starts again from the instruction audio.</p>
 */
@Slf4j
final class BreathingRoomSession extends AudioEventAdapter {

    /**
     * Stop the session if the meditation track fails to play this many times in a row.
     */
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private enum Phase {
        LOADING,
        INTRO,
        MEDITATION
    }

    @Getter(AccessLevel.PACKAGE)
    private final JDA jda;
    @Getter(AccessLevel.PACKAGE)
    private final long guildId;
    @Getter(AccessLevel.PACKAGE)
    private final Instant startedAt = Instant.now();
    @Getter(AccessLevel.PACKAGE)
    private final BreathingRoomAudioSendHandler sendHandler;

    private final AudioPlayerManager playerManager;
    private final AudioPlayer player;
    private final Path instructionsFile;
    private final Path meditationFile;
    private final Consumer<BreathingRoomSession> onFatalError;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    private volatile boolean closed;
    private volatile Phase phase = Phase.LOADING;
    private volatile AudioTrack meditationTemplate;
    private volatile ScheduledFuture<?> pendingDisconnect;

    BreathingRoomSession(
            JDA jda,
            long guildId,
            AudioPlayerManager playerManager,
            Path instructionsFile,
            Path meditationFile,
            Consumer<BreathingRoomSession> onFatalError) {
        this.jda = jda;
        this.guildId = guildId;
        this.playerManager = playerManager;
        this.instructionsFile = instructionsFile;
        this.meditationFile = meditationFile;
        this.onFatalError = onFatalError;
        this.player = playerManager.createPlayer();
        this.player.addListener(this);
        this.sendHandler = new BreathingRoomAudioSendHandler(player);
    }

    // ------------------------------------------------------------------
    // Disconnect timer bookkeeping (guarded by the service's lock)
    // ------------------------------------------------------------------

    boolean hasPendingDisconnect() {
        ScheduledFuture<?> future = pendingDisconnect;
        return future != null && !future.isDone();
    }

    void setPendingDisconnect(ScheduledFuture<?> future) {
        this.pendingDisconnect = future;
    }

    /**
     * Cancels a pending disconnect, if any.
     *
     * @return {@code true} if a pending disconnect existed and was cancelled
     */
    boolean cancelPendingDisconnect() {
        ScheduledFuture<?> future = pendingDisconnect;
        pendingDisconnect = null;
        if (future == null || future.isDone()) {
            return false;
        }
        future.cancel(false);
        return true;
    }

    // ------------------------------------------------------------------
    // Playback
    // ------------------------------------------------------------------

    /**
     * Loads both files asynchronously, then plays the intro (or goes straight to meditation).
     */
    void start() {
        CompletableFuture<AudioTrack> meditationFuture = loadTrack(meditationFile);

        // A missing or broken intro file must not stop the meditation itself.
        CompletableFuture<AudioTrack> introFuture = loadTrack(instructionsFile).handle((track, error) -> {
            if (error != null) {
                log.warn("Could not load Breathing Room instructions audio; skipping it: {}", error.toString());
                return null;
            }
            return track;
        });

        meditationFuture
                .thenAcceptBoth(introFuture, this::onTracksLoaded)
                .exceptionally(error -> {
                    log.error("Could not load Breathing Room meditation audio.", error);
                    if (!closed) {
                        onFatalError.accept(this);
                    }
                    return null;
                });
    }

    private void onTracksLoaded(AudioTrack meditation, AudioTrack intro) {
        if (closed) {
            return;
        }
        this.meditationTemplate = meditation;
        try {
            if (intro != null) {
                phase = Phase.INTRO;
                log.info("Breathing Room (guild {}): playing instruction audio.", guildId);
                player.startTrack(intro.makeClone(), false);
            } else {
                startMeditation();
            }
        } catch (RuntimeException e) {
            log.error("Failed to start Breathing Room playback.", e);
            onFatalError.accept(this);
        }
    }

    private void startMeditation() {
        AudioTrack template = meditationTemplate;
        if (closed || template == null) {
            return;
        }
        phase = Phase.MEDITATION;
        player.startTrack(template.makeClone(), false);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        // STOPPED / REPLACED / CLEANUP mean we (or LavaPlayer) ended it on purpose.
        if (closed || !endReason.mayStartNext) {
            return;
        }

        if (endReason == AudioTrackEndReason.FINISHED) {
            consecutiveFailures.set(0);
        } else {
            int failures = consecutiveFailures.incrementAndGet();
            log.warn("Breathing Room track failed to load/play (attempt {}/{}).", failures, MAX_CONSECUTIVE_FAILURES);
            if (failures >= MAX_CONSECUTIVE_FAILURES) {
                log.error("Breathing Room audio keeps failing; stopping the session.");
                onFatalError.accept(this);
                return;
            }
        }

        try {
            if (phase == Phase.INTRO) {
                log.info("Breathing Room (guild {}): instruction audio finished, starting meditation loop.", guildId);
            }
            startMeditation();
        } catch (RuntimeException e) {
            log.error("Failed to continue Breathing Room playback.", e);
            onFatalError.accept(this);
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        log.warn("Breathing Room track exception: {}", exception.getMessage());
    }

    /**
     * Stops playback and releases the player. Safe to call more than once.
     */
    void close() {
        closed = true;
        cancelPendingDisconnect();
        try {
            player.removeListener(this);
            player.stopTrack();
            player.destroy();
        } catch (RuntimeException e) {
            log.warn("Error while closing Breathing Room player: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------
    // Track loading
    // ------------------------------------------------------------------

    private CompletableFuture<AudioTrack> loadTrack(Path file) {
        CompletableFuture<AudioTrack> future = new CompletableFuture<>();

        if (!Files.isRegularFile(file)) {
            future.completeExceptionally(new IllegalStateException("Audio file not found: " + file));
            return future;
        }

        playerManager.loadItem(file.toString(), new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                future.complete(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                List<AudioTrack> tracks = playlist.getTracks();
                if (tracks.isEmpty()) {
                    future.completeExceptionally(new IllegalStateException("Audio file is empty: " + file));
                } else {
                    future.complete(tracks.get(0));
                }
            }

            @Override
            public void noMatches() {
                future.completeExceptionally(new IllegalStateException("No playable audio found at: " + file));
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                future.completeExceptionally(exception);
            }
        });

        return future;
    }
}
