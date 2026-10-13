package com.scsabot.discordbot.roles.interest;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.scsabot.discordbot.roles.ReactionRoleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Persists the IDs of the published interest-picker messages per guild in
 * data/interest-roles.properties. Kept separate from {@link ReactionRoleStore} so the
 * country and interest pickers never overwrite or clear each other's messages.
 */
@Component
public class InterestRoleStore {

    private static final Logger log = LoggerFactory.getLogger(InterestRoleStore.class);
    private static final Path FILE = Path.of("data", "interest-roles.properties");

    private final Properties props = new Properties();

    public InterestRoleStore() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                props.load(reader);
            } catch (IOException e) {
                log.error("Could not read {}", FILE, e);
            }
        }
    }

    public synchronized List<Long> getMessageIds(long guildId) {
        String value = props.getProperty(Long.toString(guildId), "");
        List<Long> ids = new ArrayList<>();
        for (String part : value.split(",")) {
            if (!part.isBlank()) {
                ids.add(Long.parseLong(part.trim()));
            }
        }
        return ids;
    }

    public synchronized boolean isTracked(long guildId, long messageId) {
        return getMessageIds(guildId).contains(messageId);
    }

    public synchronized void addMessageId(long guildId, long messageId) {
        List<Long> ids = getMessageIds(guildId);
        if (!ids.contains(messageId)) {
            ids.add(messageId);
            put(guildId, ids);
        }
    }

    public synchronized void clear(long guildId) {
        props.remove(Long.toString(guildId));
        save();
    }

    private void put(long guildId, List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(id);
        }
        props.setProperty(Long.toString(guildId), sb.toString());
        save();
    }

    private void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                props.store(writer, "Interest-role message IDs (guildId=messageId,messageId,...)");
            }
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Could not write {}", FILE, e);
        }
    }
}
