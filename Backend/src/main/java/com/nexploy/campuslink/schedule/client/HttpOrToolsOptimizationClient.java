package com.nexploy.campuslink.schedule.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpOrToolsOptimizationClient implements OrToolsOptimizationClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpOrToolsOptimizationClient(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
            .version(Version.HTTP_1_1)
            .build();
        this.objectMapper = objectMapper;
    }

    @Value("${optimization.ortools.base-url:http://localhost:8090}")
    private String baseUrl;

    @Override
    public OptimizationResult optimize(OptimizationProblem problem) {
        try {
            String json = objectMapper.writeValueAsString(problem);
            byte[] requestBody = json.getBytes(StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/internal/optimization/schedule"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("OR-Tools returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), OptimizationResult.class);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Could not serialize OR-Tools request", exception);
        }
    }
}

