package com.networkproject.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles the four JSON API endpoints.
 */
public final class ApiHandlers {
    static final int MAX_HOST_LENGTH = 253;
    static final int MAX_ECHO_BODY_BYTES = 4_096;

    private final Instant startupTime;
    private final AtomicLong requestCount;

    public ApiHandlers(Instant startupTime, AtomicLong requestCount) {
        this.startupTime = startupTime;
        this.requestCount = requestCount;
    }

    public void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            HttpUtils.methodNotAllowed(exchange, "GET");
            return;
        }

        Instant now = Instant.now();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "running");
        response.put("serverTime", now.toString());
        response.put("uptimeSeconds", Math.max(0, Duration.between(startupTime, now).toSeconds()));
        response.put("requestCount", requestCount.get());
        response.put("javaVersion", Runtime.version().feature());
        HttpUtils.sendJson(exchange, 200, response);
    }

    public void handleDns(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            HttpUtils.methodNotAllowed(exchange, "GET");
            return;
        }

        String host = normalizedHost(HttpUtils.queryParameters(exchange).get("host"));
        if (host == null) {
            HttpUtils.sendJsonError(exchange, 400, "A host value is required");
            return;
        }
        if (host.length() > MAX_HOST_LENGTH) {
            HttpUtils.sendJsonError(exchange, 400, "Host must be at most " + MAX_HOST_LENGTH + " characters");
            return;
        }

        HttpUtils.sendJson(exchange, 200, NetworkDiagnostics.lookup(host));
    }

    public void handlePortCheck(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            HttpUtils.methodNotAllowed(exchange, "GET");
            return;
        }

        Map<String, String> parameters = HttpUtils.queryParameters(exchange);
        String host = normalizedHost(parameters.get("host"));
        if (host == null) {
            HttpUtils.sendJsonError(exchange, 400, "A host value is required");
            return;
        }
        if (host.length() > MAX_HOST_LENGTH) {
            HttpUtils.sendJsonError(exchange, 400, "Host must be at most " + MAX_HOST_LENGTH + " characters");
            return;
        }

        Integer port = parsePort(parameters.get("port"));
        if (port == null) {
            HttpUtils.sendJsonError(exchange, 400, "Port must be a number from 1 through 65535");
            return;
        }

        HttpUtils.sendJson(exchange, 200, NetworkDiagnostics.checkPort(host, port));
    }

    public void handleEcho(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            HttpUtils.methodNotAllowed(exchange, "POST");
            return;
        }

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String mediaType = contentType == null ? "" : contentType.split(";", 2)[0].trim();
        if (!"application/json".equalsIgnoreCase(mediaType)) {
            HttpUtils.sendJsonError(exchange, 415, "Content-Type must be application/json");
            return;
        }

        byte[] body;
        try {
            body = HttpUtils.readLimitedBody(exchange.getRequestBody(), MAX_ECHO_BODY_BYTES);
        } catch (HttpUtils.PayloadTooLargeException exception) {
            HttpUtils.sendJsonError(exchange, 413,
                    "Request body must not exceed " + MAX_ECHO_BODY_BYTES + " bytes");
            return;
        }

        JsonNode root;
        try {
            root = HttpUtils.JSON.readTree(new String(body, StandardCharsets.UTF_8));
        } catch (JsonProcessingException exception) {
            HttpUtils.sendJsonError(exchange, 400, "Request body must contain valid JSON");
            return;
        }

        JsonNode messageNode = root == null ? null : root.get("message");
        if (messageNode == null || !messageNode.isTextual()) {
            HttpUtils.sendJsonError(exchange, 400, "A string message field is required");
            return;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", messageNode.textValue());
        response.put("serverTime", Instant.now().toString());
        HttpUtils.sendJson(exchange, 200, response);
    }

    private static String normalizedHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            return null;
        }
        return host.trim();
    }

    private static Integer parsePort(String value) {
        if (value == null) {
            return null;
        }
        try {
            int port = Integer.parseInt(value.trim());
            return port >= 1 && port <= 65_535 ? port : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
