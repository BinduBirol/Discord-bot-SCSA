package com.scsabot.discordbot.story;

import com.scsabot.discordbot.dto.StoryImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Picks a random configured image provider (Pexels or Unsplash) and fetches
 * a photo from it. If the chosen provider fails, it falls back to the next
 * one so a rate limit or outage on one site doesn't break /story image.
 */
@Component
public class RandomImageService {

    private static final Logger log =
            LoggerFactory.getLogger(RandomImageService.class);

    private final List<ImageProvider> providers;

    // Spring injects every ImageProvider bean (Pexels + Unsplash) here.
    public RandomImageService(List<ImageProvider> providers) {
        this.providers = providers;

        providers.forEach(p -> log.info("Image provider registered: {} (configured={})",
                p.getClass().getSimpleName(), p.isConfigured()));
    }

    /**
     * Returns a random photo, or null if no provider is configured
     * or every provider failed.
     */
    public StoryImage fetchRandomImage() {

        List<ImageProvider> candidates = new ArrayList<>(
                providers.stream()
                        .filter(ImageProvider::isConfigured)
                        .toList()
        );

        if (candidates.isEmpty()) {
            log.warn("No image providers are configured");
            return null;
        }

        // Shuffled order = random first choice, remaining ones act as fallbacks.
        Collections.shuffle(candidates);

        for (ImageProvider provider : candidates) {
            String name = provider.getClass().getSimpleName();
            StoryImage image = provider.fetchRandomImage();
            if (image != null) {
                log.info("{} succeeded: {}", name, image.imageUrl());
                return image;
            }
            log.warn("{} returned no image, trying next provider", name);
        }

        return null;
    }
}