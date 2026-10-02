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

/**
 * Fetches a random photo from the Unsplash API.
 * Requires an Unsplash access key (https://unsplash.com/developers)
 * configured as unsplash.access.key.
 * <p>
 * Unsplash guidelines require crediting the photographer and linking
 * back to Unsplash (handled in StoryImageCommand's embed). Demo apps are
 * limited to 50 requests/hour until approved for production.
 */
@Component
public class UnsplashImageService implements ImageProvider {

    private static final Logger log =
            LoggerFactory.getLogger(UnsplashImageService.class);

    private static final String RANDOM_URL = "https://api.unsplash.com/photos/random";
    private static final String UTM = "?utm_source=scsabot&utm_medium=referral";

    private static final List<String> KEYWORDS = List.of(
            "people", "street", "restaurant", "airport", "family",
            "office", "school", "market", "nature", "animals"
    );

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final String accessKey;

    public UnsplashImageService(
            ObjectMapper objectMapper,
            @Value("${unsplash.access.key:}") String accessKey
    ) {
        this.objectMapper = objectMapper;
        this.accessKey = accessKey;

        // Startup check: logs whether the key loaded, never the key itself.
        log.info("Unsplash key loaded: {} (length={})",
                accessKey != null && !accessKey.isBlank(),
                accessKey == null ? 0 : accessKey.length());
    }

    @Override
    public boolean isConfigured() {
        return accessKey != null && !accessKey.isBlank();
    }

    @Override
    public StoryImage fetchRandomImage() {

        if (!isConfigured()) {
            log.warn("Unsplash access key is not configured (unsplash.access.key)");
            return null;
        }

        String keyword = KEYWORDS.get(
                ThreadLocalRandom.current().nextInt(KEYWORDS.size())
        );

        try {
            String url = RANDOM_URL
                    + "?query=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                    + "&orientation=landscape";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Client-ID " + accessKey)
                    .header("Accept-Version", "v1")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Unsplash API returned status {} for keyword '{}': {}",
                        response.statusCode(), keyword, response.body());
                return null;
            }

            JsonNode photo = objectMapper.readTree(response.body());

            String imageUrl = photo.path("urls").path("regular").asText(null);
            String photographer = photo.path("user").path("name").asText("Unknown");
            String pageUrl = photo.path("links").path("html").asText(null);

            if (imageUrl == null) {
                log.warn("Unsplash response had no image URL for keyword '{}'", keyword);
                return null;
            }

            if (pageUrl != null) {
                pageUrl += UTM;
            }

            log.info("Unsplash fetched photo for keyword '{}'", keyword);
            return new StoryImage(imageUrl, photographer, pageUrl, "Unsplash");

        } catch (IOException e) {
            log.error("Failed to fetch image from Unsplash", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Unsplash request interrupted", e);
            return null;
        }
    }
}