package com.scsabot.discordbot.breathing;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Configuration for the StamUnity Breathing Room feature.
 *
 * <p>All keys live under {@code stamunity.breathing-room.*}. The feature stays
 * disabled until both channel IDs are configured. Audio files default to
 * {@code data/audio/instructions.mp3} and {@code data/audio/meditation.mp3},
 * relative to the folder the bot is started from.</p>
 */
@Slf4j
@Component
public class BreathingRoomProperties {

    static final String DEFAULT_WELCOME_MESSAGE =
            "Welcome to the StamUnity Breathing Room, {mention}! "
                    + "Please get comfortable and keep your microphone muted. "
                    + "There is nothing you need to say or do - just listen to the guidance, "
                    + "breathe slowly, and go at your own pace.";

    @Getter
    private final long voiceChannelId;
    @Getter
    private final long textChannelId;
    @Getter
    private final Path instructionsFile;
    @Getter
    private final Path meditationFile;
    @Getter
    private final Duration disconnectDelay;

    private final String welcomeMessageTemplate;

    public BreathingRoomProperties(
            @Value("${stamunity.breathing-room.voice-channel-id:}") String voiceChannelId,
            @Value("${stamunity.breathing-room.text-channel-id:}") String textChannelId,
            @Value("${stamunity.breathing-room.instructions-file:data/audio/instructions.mp3}") String instructionsFile,
            @Value("${stamunity.breathing-room.meditation-file:data/audio/meditation.mp3}") String meditationFile,
            @Value("${stamunity.breathing-room.disconnect-delay-seconds:30}") long disconnectDelaySeconds,
            @Value("${stamunity.breathing-room.welcome-message:}") String welcomeMessage) {
        this.voiceChannelId = parseId("voice-channel-id", voiceChannelId);
        this.textChannelId = parseId("text-channel-id", textChannelId);
        this.instructionsFile = Path.of(instructionsFile.trim()).toAbsolutePath().normalize();
        this.meditationFile = Path.of(meditationFile.trim()).toAbsolutePath().normalize();
        this.disconnectDelay = Duration.ofSeconds(Math.max(0, disconnectDelaySeconds));
        this.welcomeMessageTemplate =
                (welcomeMessage == null || welcomeMessage.isBlank())
                        ? DEFAULT_WELCOME_MESSAGE
                        : welcomeMessage;

        if (isEnabled()) {
            log.info(
                    "Breathing Room enabled: voiceChannel={}, textChannel={}, intro={}, meditation={}, disconnectDelay={}s",
                    this.voiceChannelId,
                    this.textChannelId,
                    this.instructionsFile,
                    this.meditationFile,
                    this.disconnectDelay.toSeconds()
            );
        } else {
            log.info("Breathing Room is disabled (voice-channel-id and text-channel-id are not both configured).");
        }
    }

    public boolean isEnabled() {
        return voiceChannelId > 0 && textChannelId > 0;
    }

    /**
     * Builds the welcome message for a newly joined member. If the configured
     * template has no {@code {mention}} placeholder, the mention is prepended so
     * the member is always tagged.
     */
    public String formatWelcomeMessage(String mention) {
        if (welcomeMessageTemplate.contains("{mention}")) {
            return welcomeMessageTemplate.replace("{mention}", mention);
        }
        return mention + " " + welcomeMessageTemplate;
    }

    private static long parseId(String key, String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid value '{}' for stamunity.breathing-room.{}; expected a numeric Discord ID.", raw, key);
            return 0L;
        }
    }
}
