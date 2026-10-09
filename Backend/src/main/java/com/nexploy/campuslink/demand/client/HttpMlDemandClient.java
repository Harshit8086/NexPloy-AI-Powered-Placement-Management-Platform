package com.nexploy.campuslink.demand.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpMlDemandClient implements MlDemandClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpMlDemandClient(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder().version(Version.HTTP_1_1).build();
        this.objectMapper = objectMapper;
    }

    @Value("${ml.base-url:http://localhost:8091}")
    private String baseUrl;

    @Override
    public DemandForecastResult forecast(DemandForecastRequest request) {
        try {
            byte[] body = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/internal/ml/v1/demand:forecast"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                    "ML service returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), DemandForecastResult.class);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Could not call ML demand endpoint", exception);
        }
    }
}
