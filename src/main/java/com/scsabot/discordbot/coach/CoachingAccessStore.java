package com.scsabot.discordbot.coach;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Persists the ID of each published coach card per guild in data/coaching-access.properties
 * (key: guildId.coachKey, value: messageId).
 */
@Component
public class CoachingAccessStore {

    private static final Logger log = LoggerFactory.getLogger(CoachingAccessStore.class);
    private static final Path FILE = Path.of("data", "coaching-access.properties");

    private final Properties props = new Properties();

    public CoachingAccessStore() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                props.load(reader);
            } catch (IOException e) {
                log.error("Could not read {}", FILE, e);
            }
        }
    }

    private static String key(long guildId, String coachKey) {
        return guildId + "." + coachKey;
    }

    public synchronized Optional<Long> getMessageId(long guildId, String coachKey) {
        String value = props.getProperty(key(guildId, coachKey));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value.trim()));
    }

    public synchronized boolean isTracked(long guildId, String coachKey, long messageId) {
        return getMessageId(guildId, coachKey).filter(id -> id == messageId).isPresent();
    }

    public synchronized void setMessageId(long guildId, String coachKey, long messageId) {
        props.setProperty(key(guildId, coachKey), Long.toString(messageId));
        save();
    }

    public synchronized void clear(long guildId, String coachKey) {
        props.remove(key(guildId, coachKey));
        save();
    }

    private void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                props.store(writer, "Coaching-access message IDs (guildId.coachKey=messageId)");
            }
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Could not write {}", FILE, e);
        }
    }
}