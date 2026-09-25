package com.scsabot.discordbot.story;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads public image URLs for /story image from data/images/*.json.
 * Each file is a plain JSON array of URL strings, e.g.:
 * ["https://pub-xxxx.r2.dev/lighthouse.jpg", "https://pub-xxxx.r2.dev/market.jpg"]
 * <p>
 * Images themselves live in a public Cloudflare R2 bucket (or any public
 * host) — this loader only tracks the URLs, so the bot never needs to
 * touch the storage API at runtime.
 */
@Component
public class StoryImageDataLoader {

    private static final Logger log =
            LoggerFactory.getLogger(StoryImageDataLoader.class);

    private static final Path IMAGES_PATH =
            Path.of("data/images");

    private final ObjectMapper objectMapper;

    private final List<String> imageUrls =
            new ArrayList<>();

    public StoryImageDataLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        loadImages();

        log.info(
                "StoryImage loaded {} image URLs",
                imageUrls.size()
        );
    }

    private void loadImages() {

        if (!Files.exists(IMAGES_PATH)) {
            log.warn(
                    "Story images directory does not exist: {}",
                    IMAGES_PATH
            );
            return;
        }

        try (Stream<Path> files = Files.list(IMAGES_PATH)) {

            files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName()
                            .toString()
                            .endsWith(".json"))
                    .forEach(this::loadImageFile);

        } catch (IOException e) {
            log.error(
                    "Failed to scan story images directory",
                    e
            );
        }
    }

    private void loadImageFile(Path path) {

        try (InputStream inputStream = Files.newInputStream(path)) {

            List<String> loaded =
                    objectMapper.readValue(
                            inputStream,
                            new TypeReference<List<String>>() {
                            }
                    );

            if (loaded == null) {
                return;
            }

            loaded.stream()
                    .filter(url -> url != null && !url.isBlank())
                    .map(String::trim)
                    .forEach(imageUrls::add);

            log.info(
                    "Loaded {} image URLs from {}",
                    loaded.size(),
                    path
            );

        } catch (Exception e) {

            log.error(
                    "Failed to load story image file: {}",
                    path,
                    e
            );
        }
    }

    public List<String> getImageUrls() {
        return Collections.unmodifiableList(imageUrls);
    }
}
