package com.nexploy.campuslink.shared.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.matching.client.HttpMlMatchingClient;
import com.nexploy.campuslink.matching.client.MlMatchingClient.MatchingRequest;
import com.nexploy.campuslink.matching.client.MlMatchingClient.MatchingResult;
import com.nexploy.campuslink.schedule.client.HttpOrToolsOptimizationClient;
import com.nexploy.campuslink.schedule.client.OrToolsOptimizationClient.OptimizationProblem;
import com.nexploy.campuslink.schedule.client.OrToolsOptimizationClient.OptimizationResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Live-HTTP smoke test for the outbound ML / OR-Tools clients. Unlike the mocked contract tests,
 * this drives the real {@code java.net.http.HttpClient.send()} path against a tiny in-process HTTP
 * server, so it catches client-side request-building regressions — specifically the class of bug
 * where a client set a restricted header (Content-Length), which threw before any request left the
 * JVM and returned HTTP 400 to callers. All six outbound clients share this implementation; a
 * representative ML client and the OR-Tools client are exercised here. No Python service required.
 */
class MlClientLiveHttpTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private HttpServer startStub(String responseJson) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            // Drain the request body so the client's write completes, then respond 200.
            exchange.getRequestBody().readAllBytes();
            byte[] out = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        return server;
    }

    @Test
    void mlMatchingClientCompletesRealHttpRoundTrip() throws IOException {
        HttpServer server = startStub(
            "{\"drive_id\":\"drv_1\",\"ranked\":[],\"model_version\":\"m1\","
                + "\"feature_version\":\"f1\",\"generated_at\":\"2026-01-01T00:00:00Z\"}");
        try {
            HttpMlMatchingClient client = new HttpMlMatchingClient(objectMapper);
            ReflectionTestUtils.setField(client, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());

            MatchingResult result = client.rank(new MatchingRequest(
                "req_1", "campus_demo", "current", "policy-test", "drv_1", List.of(), List.of()));

            // A parsed result means the request was built + sent without the restricted-header throw.
            assertThat(result).isNotNull();
            assertThat(result.driveId()).isEqualTo("drv_1");
            assertThat(result.modelVersion()).isEqualTo("m1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void orToolsClientCompletesRealHttpRoundTrip() throws IOException {
        HttpServer server = startStub(
            "{\"status\":\"OPTIMAL\",\"hard_violations\":0,\"soft_score\":0,"
                + "\"alternatives\":[],\"selected_assignments\":[]}");
        try {
            HttpOrToolsOptimizationClient client = new HttpOrToolsOptimizationClient(objectMapper);
            ReflectionTestUtils.setField(client, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());

            OptimizationResult result = client.optimize(new OptimizationProblem(
                "v1", List.of(), List.of(), List.of(), List.of(), List.of()));

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo("OPTIMAL");
        } finally {
            server.stop(0);
        }
    }
}
