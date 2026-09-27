package com.scsabot.discordbot.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scsabot.discordbot.dto.ResourceEntry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Finds every file matching data/resources/resource*.json, parses each as a
 * JSON array of ResourceEntry, and holds the combined list in memory.
 * <p>
 * NOTE: on Spring Boot 2.x, change the PostConstruct import to
 * javax.annotation.PostConstruct instead of jakarta.annotation.PostConstruct.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ResourceRepository {

    private final ResourceBotProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile List<ResourceEntry> entries = Collections.emptyList();

    @PostConstruct
    public void load() {
        reload();
    }

    /**
     * Re-scans the resources directory. Safe to call again later (e.g. from
     * an admin command) if you add new resourceN.json files while running.
     */
    public synchronized void reload() {
        Path directory = Paths.get(properties.getDirectory());
        List<ResourceEntry> loaded = new ArrayList<>();

        if (!Files.isDirectory(directory)) {
            log.warn("Resource directory '{}' does not exist - no entries loaded.", directory);
            this.entries = loaded;
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, properties.getFilePattern())) {
            List<Path> files = new ArrayList<>();
            stream.forEach(files::add);
            files.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));

            for (Path file : files) {
                try {
                    ResourceEntry[] fileEntries = objectMapper.readValue(file.toFile(), ResourceEntry[].class);
                    Collections.addAll(loaded, fileEntries);
                    log.info("Loaded {} entries from {}", fileEntries.length, file.getFileName());
                } catch (IOException e) {
                    log.error("Failed to parse resource file {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan resource directory {}: {}", directory, e.getMessage());
        }

        this.entries = Collections.unmodifiableList(loaded);
        log.info("Resource repository now holds {} total entries from {}.", this.entries.size(), directory);
    }

    public List<ResourceEntry> getEntries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }
}