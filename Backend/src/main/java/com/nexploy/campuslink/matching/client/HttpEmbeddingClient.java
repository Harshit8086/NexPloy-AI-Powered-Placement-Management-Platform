package com.nexploy.campuslink.matching.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpEmbeddingClient implements EmbeddingClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpEmbeddingClient(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder().version(Version.HTTP_1_1).build();
        this.objectMapper = objectMapper;
    }

    @Value("${ml.base-url:http://localhost:8091}")
    private String baseUrl;

    @Override
    public List<Double> embed(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return null;
        }
        try {
            byte[] body = objectMapper.writeValueAsString(new EmbeddingRequest(terms))
                .getBytes(StandardCharsets.UTF_8);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/internal/ml/v1/embedding:embed"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                    "ML service returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), EmbeddingResult.class).embedding();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Could not call ML embedding endpoint", exception);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record EmbeddingRequest(List<String> terms) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record EmbeddingResult(List<Double> embedding, int dim) {
    }
}
