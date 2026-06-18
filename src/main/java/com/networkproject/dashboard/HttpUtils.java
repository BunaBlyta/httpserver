package com.networkproject.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared HTTP parsing and response helpers.
 */
public final class HttpUtils {
    public static final ObjectMapper JSON = new ObjectMapper();

    private HttpUtils() {
    }

    public static Map<String, String> queryParameters(HttpExchange exchange) {
        Map<String, String> parameters = new LinkedHashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return parameters;
        }

        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length == 2 ? decode(parts[1]) : "";
            parameters.put(key, value);
        }
        return parameters;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public static byte[] readLimitedBody(InputStream input, int maximumBytes) throws IOException {
        byte[] body = input.readNBytes(maximumBytes + 1);
        if (body.length > maximumBytes) {
            throw new PayloadTooLargeException();
        }
        return body;
    }

    public static void sendJson(HttpExchange exchange, int statusCode, Object value) throws IOException {
        try {
            sendBytes(exchange, statusCode, "application/json; charset=utf-8", JSON.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IOException("Could not create JSON response", exception);
        }
    }

    public static void sendJsonError(HttpExchange exchange, int statusCode, String message) throws IOException {
        sendJson(exchange, statusCode, Map.of("success", false, "message", message));
    }

    public static void sendText(HttpExchange exchange, int statusCode, String contentType, String text)
            throws IOException {
        sendBytes(exchange, statusCode, contentType + "; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    }

    public static void sendBytes(HttpExchange exchange, int statusCode, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    public static void methodNotAllowed(HttpExchange exchange, String allowedMethod) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowedMethod);
        sendJsonError(exchange, 405, "Method not allowed. Use " + allowedMethod + ".");
    }

    public static final class PayloadTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
