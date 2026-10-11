package com.scsabot.discordbot.roleplay.loader;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scsabot.discordbot.dto.RolePlayScenario;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Loads roleplay scenarios from data/roleplay/{filePrefix}*.json.
 * Each file is a JSON array of RolePlayScenario objects.
 */
public abstract class RolePlayDataLoader {

    private static final Path ROLEPLAY_PATH = Path.of("data/roleplay");

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ObjectMapper objectMapper;
    private final int expectedPlayerCount;
    private final String filePrefix;

    private final List<RolePlayScenario> scenarios = new ArrayList<>();

    protected RolePlayDataLoader(ObjectMapper objectMapper,
                                 int expectedPlayerCount,
                                 String filePrefix) {
        this.objectMapper = objectMapper;
        this.expectedPlayerCount = expectedPlayerCount;
        this.filePrefix = filePrefix;
    }

    @PostConstruct
    public void load() {
        loadScenarios();

        log.info("RolePlay {}P loaded {} scenarios",
                expectedPlayerCount, scenarios.size());
    }

    private void loadScenarios() {

        if (!Files.exists(ROLEPLAY_PATH)) {
            log.warn("Roleplay directory does not exist: {}", ROLEPLAY_PATH);
            return;
        }

        try (Stream<Path> files = Files.list(ROLEPLAY_PATH)) {

            files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(filePrefix) && name.endsWith(".json");
                    })
                    .sorted()
                    .forEach(this::loadFile);

        } catch (IOException e) {
            log.error("Failed to scan roleplay directory", e);
        }
    }

    private void loadFile(Path path) {

        try (InputStream inputStream = Files.newInputStream(path)) {

            List<RolePlayScenario> loaded =
                    objectMapper.readValue(
                            inputStream,
                            new TypeReference<List<RolePlayScenario>>() {
                            }
                    );

            if (loaded == null) {
                return;
            }

            int added = 0;
            for (RolePlayScenario scenario : loaded) {
                if (scenario == null) {
                    continue;
                }
                if (scenario.playerCount() != expectedPlayerCount) {
                    log.warn("Skipping {} in {}: playerCount is {}, expected {}",
                            scenario.id(), path, scenario.playerCount(),
                            expectedPlayerCount);
                    continue;
                }
                scenarios.add(scenario);
                added++;
            }

            log.info("Loaded {} scenarios from {}", added, path);

        } catch (Exception e) {
            log.error("Failed to load roleplay file: {}", path, e);
        }
    }

    public List<RolePlayScenario> getScenarios() {
        return Collections.unmodifiableList(scenarios);
    }

    public Optional<RolePlayScenario> getRandom() {
        if (scenarios.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                scenarios.get(ThreadLocalRandom.current().nextInt(scenarios.size()))
        );
    }

    public List<RolePlayScenario> getByCategory(String category) {
        return scenarios.stream()
                .filter(s -> s.category().equalsIgnoreCase(category))
                .toList();
    }
}