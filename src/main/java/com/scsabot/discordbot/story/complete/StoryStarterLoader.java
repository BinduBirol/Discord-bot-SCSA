package com.scsabot.discordbot.story.complete;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.scsabot.discordbot.dto.StoryStarter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class StoryStarterLoader {

    private record StoryFile(List<StoryStarter> stories) {
    }

    private final ObjectMapper objectMapper;
    private final Path directory;

    private List<StoryStarter> stories = List.of();

    public StoryStarterLoader(
            ObjectMapper objectMapper,
            @Value("${bot.story-data-dir:data/story}") String directory
    ) {
        this.objectMapper = objectMapper;
        this.directory = Path.of(directory);
    }

    @PostConstruct
    void load() {
        List<StoryStarter> loaded = new ArrayList<>();

        try (DirectoryStream<Path> files =
                     Files.newDirectoryStream(directory, "story*.json")) {

            for (Path file : files) {
                try {
                    StoryFile parsed = objectMapper.readValue(file.toFile(), StoryFile.class);
                    if (parsed.stories() != null) {
                        loaded.addAll(parsed.stories());
                        log.info("Loaded {} stories from {}", parsed.stories().size(), file.getFileName());
                    }
                } catch (IOException e) {
                    log.warn("Could not read story file {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Could not read story directory {}: {}", directory, e.getMessage());
        }

        stories = List.copyOf(loaded);
        log.info("Total story starters loaded: {}", stories.size());
    }

    /**
     * Returns a random story, or null if none are loaded.
     */
    public StoryStarter randomStory() {
        if (stories.isEmpty()) {
            return null;
        }
        return stories.get(ThreadLocalRandom.current().nextInt(stories.size()));
    }
}