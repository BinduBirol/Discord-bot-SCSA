package com.scsabot.discordbot.story;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scsabot.discordbot.dto.StoryImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


@Component
public class PexelsImageService implements ImageProvider {

    private static final Logger log =
            LoggerFactory.getLogger(PexelsImageService.class);

    private static final String SEARCH_URL = "https://api.pexels.com/v1/search";
    private static final int RESULTS_PER_PAGE = 15;

    // Rotated randomly so /story image doesn't always show the same subject.
    private static final List<String> KEYWORDS = List.of(
            "people", "street", "restaurant", "airport", "family",
            "office", "school", "market", "nature", "animals"
    );

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public PexelsImageService(
            ObjectMapper objectMapper,
            @Value("${pexels.api.key:}") String apiKey
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Fetches one random photo for a randomly chosen keyword.
     * Returns null if the API key isn't configured, the request fails,
     * or no results are returned.
     */
    @Override
    public StoryImage fetchRandomImage() {

        if (!isConfigured()) {
            log.warn("Pexels API key is not configured (pexels.api.key)");
            return null;
        }

        String keyword = KEYWORDS.get(
                ThreadLocalRandom.current().nextInt(KEYWORDS.size())
        );

        try {
            String url = SEARCH_URL
                    + "?query=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                    + "&per_page=" + RESULTS_PER_PAGE;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Pexels API returned status {} for keyword '{}': {}",
                        response.statusCode(), keyword, response.body());
                return null;
            }

            JsonNode photos = objectMapper.readTree(response.body()).get("photos");

            if (photos == null || !photos.isArray() || photos.isEmpty()) {
                log.warn("Pexels API returned no photos for keyword '{}'", keyword);
                return null;
            }

            JsonNode photo = photos.get(
                    ThreadLocalRandom.current().nextInt(photos.size())
            );

            String imageUrl = photo.path("src").path("large").asText(null);
            if (imageUrl == null) {
                return null;
            }

            return new StoryImage(
                    imageUrl,
                    photo.path("photographer").asText("Unknown"),
                    photo.path("url").asText(null),
                    "Pexels"
            );

        } catch (IOException e) {
            log.error("Failed to fetch image from Pexels", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Pexels request interrupted", e);
            return null;
        }
    }
}