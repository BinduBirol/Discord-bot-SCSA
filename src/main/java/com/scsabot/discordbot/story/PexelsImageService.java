package com.scsabot.discordbot.story;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Fetches a random photo from the Pexels API for /story image, instead of
 * hosting/curating images ourselves. Requires a free Pexels API key
 * (https://www.pexels.com/api/) configured as pexels.api.key.
 * <p>
 * Pexels guidelines require attribution: crediting the photographer and
 * linking back to Pexels wherever an image is shown (handled in
 * StoryImageCommand's embed).
 * <p>
 * Free tier limits (as of the time this was written): 200 requests/hour,
 * 20,000 requests/month. Pexels can grant higher limits on request for
 * eligible apps.
 */
@Component
public class PexelsImageService {

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

    /**
     * A fetched photo, with the attribution details Pexels requires.
     */
    public record PexelsImage(
            String imageUrl,
            String photographer,
            String pexelsPageUrl
    ) {
    }

    /**
     * Fetches one random photo for a randomly chosen keyword.
     * Returns null if the API key isn't configured, the request fails,
     * or no results are returned — callers should handle that gracefully.
     */
    public PexelsImage fetchRandomImage() {

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Pexels API key is not configured (pexels.api.key)");
            return null;
        }

        String keyword = KEYWORDS.get(
                ThreadLocalRandom.current().nextInt(KEYWORDS.size())
        );

        try {
            String encodedQuery = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = SEARCH_URL
                    + "?query=" + encodedQuery
                    + "&per_page=" + RESULTS_PER_PAGE;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error(
                        "Pexels API returned status {} for keyword '{}': {}",
                        response.statusCode(),
                        keyword,
                        response.body()
                );
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode photos = root.get("photos");

            if (photos == null || !photos.isArray() || photos.isEmpty()) {
                log.warn("Pexels API returned no photos for keyword '{}'", keyword);
                return null;
            }

            int index = ThreadLocalRandom.current().nextInt(photos.size());
            JsonNode photo = photos.get(index);

            String imageUrl = photo.path("src").path("large").asText(null);
            String photographer = photo.path("photographer").asText("Unknown");
            String pageUrl = photo.path("url").asText(null);

            if (imageUrl == null) {
                return null;
            }

            return new PexelsImage(imageUrl, photographer, pageUrl);

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