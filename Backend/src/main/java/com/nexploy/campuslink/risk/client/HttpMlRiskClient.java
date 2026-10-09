package com.nexploy.campuslink.risk.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpMlRiskClient implements MlRiskClient {

    /**
     * Both ends of the call are bounded: a hanging ML service must not pin the predict request
     * thread indefinitely (same policy as the readiness client). A timeout surfaces as an
     * IllegalStateException from {@link #predict}.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpMlRiskClient(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
            .version(Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
        this.objectMapper = objectMapper;
    }

    @Value("${ml.base-url:http://localhost:8091}")
    private String baseUrl;

    @Override
    public RiskPrediction predict(RiskPredictRequest request) {
        try {
            byte[] body = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/internal/ml/v1/risk:predict"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                    "ML service returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), RiskPrediction.class);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Could not call ML risk endpoint", exception);
        }
    }
}
