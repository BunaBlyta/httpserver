package com.networkproject.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkDashboardServerTest {
    private NetworkDashboardServer server;
    private ExecutorService clientExecutor;
    private HttpClient client;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = new NetworkDashboardServer(0);
        server.start();
        clientExecutor = Executors.newFixedThreadPool(6);
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .executor(clientExecutor)
                .build();
        baseUri = URI.create("http://127.0.0.1:" + server.getPort());
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        if (server != null) {
            server.close();
        }
        if (clientExecutor != null) {
            clientExecutor.shutdownNow();
            clientExecutor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void serverStartsOnDynamicallySelectedPort() {
        assertTrue(server.getPort() > 0);
    }

    @Test
    void rootServesHtmlPage() throws Exception {
        HttpResponse<String> response = get("/");

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(response.body().contains("Java HTTP Server"));
    }

    @Test
    void cssAndJavaScriptResourcesAreServed() throws Exception {
        HttpResponse<String> css = get("/styles.css");
        HttpResponse<String> javascript = get("/app.js");

        assertEquals(200, css.statusCode());
        assertTrue(css.headers().firstValue("Content-Type").orElse("").startsWith("text/css"));
        assertTrue(css.body().contains(":root"));
        assertEquals(200, javascript.statusCode());
        assertTrue(javascript.headers().firstValue("Content-Type").orElse("").startsWith("text/javascript"));
        assertTrue(javascript.body().contains("fetch"));
    }

    @Test
    void unknownPathReturns404() throws Exception {
        HttpResponse<String> response = get("/does-not-exist");

        assertEquals(404, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
    }

    @Test
    void statusReturnsExpectedFields() throws Exception {
        HttpResponse<String> response = get("/api/status");
        JsonNode json = json(response);

        assertEquals(200, response.statusCode());
        assertEquals("running", json.get("status").asText());
        assertNotNull(json.get("serverTime"));
        assertTrue(json.get("uptimeSeconds").asLong() >= 0);
        assertTrue(json.get("requestCount").asLong() >= 1);
        assertEquals(21, json.get("javaVersion").asInt());
    }

    @Test
    void dnsLookupResolvesLocalhost() throws Exception {
        HttpResponse<String> response = get("/api/dns?host=localhost");
        JsonNode json = json(response);

        assertEquals(200, response.statusCode());
        assertTrue(json.get("success").asBoolean());
        assertEquals("localhost", json.get("host").asText());
        assertFalse(json.get("addresses").isEmpty());
    }

    @Test
    void missingDnsHostReturns400() throws Exception {
        HttpResponse<String> response = get("/api/dns");

        assertEquals(400, response.statusCode());
        assertFalse(json(response).get("success").asBoolean());
    }

    @Test
    void portCheckSucceedsAgainstLocalListener() throws Exception {
        try (ServerSocket listener = new ServerSocket()) {
            listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            String path = "/api/port-check?host=127.0.0.1&port=" + listener.getLocalPort();

            HttpResponse<String> response = get(path);
            JsonNode json = json(response);

            assertEquals(200, response.statusCode());
            assertTrue(json.get("success").asBoolean());
            assertTrue(json.get("open").asBoolean());
            assertEquals(listener.getLocalPort(), json.get("port").asInt());
        }
    }

    @Test
    void invalidPortReturns400() throws Exception {
        List<String> invalidPaths = List.of(
                "/api/port-check?host=localhost&port=0",
                "/api/port-check?host=localhost&port=65536",
                "/api/port-check?host=localhost&port=not-a-number");

        for (String path : invalidPaths) {
            assertEquals(400, get(path).statusCode());
        }
    }

    @Test
    void echoReturnsOrdinaryText() throws Exception {
        HttpResponse<String> response = postJson("/api/echo", "{\"message\":\"Hello\"}");
        JsonNode json = json(response);

        assertEquals(200, response.statusCode());
        assertTrue(json.get("success").asBoolean());
        assertEquals("Hello", json.get("message").asText());
        assertNotNull(json.get("serverTime"));
    }

    @Test
    void echoPreservesUnicodeText() throws Exception {
        String message = "Zdravo, 世界 🌍";
        String body = HttpUtils.JSON.writeValueAsString(java.util.Map.of("message", message));

        HttpResponse<String> response = postJson("/api/echo", body);

        assertEquals(200, response.statusCode());
        assertEquals(message, json(response).get("message").asText());
    }

    @Test
    void malformedEchoJsonReturns400() throws Exception {
        HttpResponse<String> response = postJson("/api/echo", "{\"message\":");

        assertEquals(400, response.statusCode());
        assertFalse(json(response).get("success").asBoolean());
    }

    @Test
    void unsupportedMethodReturns405AndAllowHeader() throws Exception {
        HttpRequest request = request("/api/status")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = send(request);

        assertEquals(405, response.statusCode());
        assertEquals("GET", response.headers().firstValue("Allow").orElse(""));
    }

    @Test
    void multipleRequestsCanBeHandledConcurrently() {
        List<CompletableFuture<HttpResponse<String>>> requests = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> client.sendAsync(
                        request("/api/status").GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)))
                .toList();

        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).join();

        assertTrue(requests.stream().map(CompletableFuture::join)
                .allMatch(response -> response.statusCode() == 200));
    }

    @Test
    void shutdownReleasesServerPort() throws Exception {
        int releasedPort = server.getPort();
        server.close();

        try (ServerSocket replacement = new ServerSocket()) {
            replacement.setReuseAddress(true);
            replacement.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), releasedPort));
            assertEquals(releasedPort, replacement.getLocalPort());
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(request(path).GET().build());
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpRequest request = request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(4));
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private JsonNode json(HttpResponse<String> response) throws IOException {
        return HttpUtils.JSON.readTree(response.body());
    }
}
