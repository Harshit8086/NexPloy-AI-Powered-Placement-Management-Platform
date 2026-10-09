package com.nexploy.campuslink.student.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Optional YouTube Data API v3 enrichment: given a topic, returns a few real tutorial videos to
 * surface alongside the curated learning resources. Uses the JDK {@link HttpClient} (no new
 * dependency), matching CampusLink's lightweight external-client style.
 *
 * <p><strong>Inert by default.</strong> When {@code YOUTUBE_API_KEY} is absent (the default), no
 * network call is ever made and {@link #searchTutorials} returns an empty list, so learning-resource
 * responses look exactly as they did before. Any transport, HTTP or parse failure also degrades to
 * an empty list — enrichment never breaks the request. The key is read only from configuration
 * (env-driven), never hardcoded.
 *
 * <p>Results are real video links returned by YouTube for the query; nothing is fabricated. They are
 * clearly labelled as YouTube tutorials so they are never confused with the curated catalog.
 */
@Component
public class YouTubeClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeClient.class);

    private final String apiKey;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    public YouTubeClient(
        @Value("${nexploy.youtube.api-key:}") String apiKey,
        @Value("${nexploy.youtube.base-url:https://www.googleapis.com/youtube/v3}") String baseUrl,
        ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
    }

    /** True when a key is configured; callers can skip building queries entirely when false. */
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Up to {@code max} tutorial videos for {@code topic}, newest-relevance first. Returns an empty
     * list when disabled or on any failure — never throws.
     */
    public List<Video> searchTutorials(String topic, int max) {
        if (!isEnabled() || topic == null || topic.isBlank()) {
            return List.of();
        }
        int limit = Math.max(1, Math.min(max, 5));
        try {
            String query = URLEncoder.encode(topic + " tutorial", StandardCharsets.UTF_8);
            URI uri = URI.create(baseUrl
                + "/search?part=snippet&type=video&maxResults=" + limit
                + "&q=" + query
                + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
            HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("YouTube search returned HTTP {}; skipping enrichment", response.statusCode());
                return List.of();
            }
            return parse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (RuntimeException | java.io.IOException e) {
            log.warn("YouTube search failed; skipping enrichment: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Video> parse(String body) {
        List<Video> videos = new ArrayList<>();
        try {
            JsonNode items = objectMapper.readTree(body).path("items");
            for (JsonNode item : items) {
                String videoId = item.path("id").path("videoId").asText(null);
                JsonNode snippet = item.path("snippet");
                String title = snippet.path("title").asText(null);
                String channel = snippet.path("channelTitle").asText("");
                if (videoId == null || title == null) {
                    continue;
                }
                videos.add(new Video(
                    title, channel, "https://www.youtube.com/watch?v=" + videoId));
            }
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to parse YouTube response: {}", e.getMessage());
        }
        return videos;
    }

    /** One tutorial video: its title, channel and watch URL. */
    public record Video(String title, String channel, String url) {
    }
}
