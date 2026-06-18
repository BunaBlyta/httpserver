package com.networkproject.dashboard;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Serves the dashboard files from the packaged classpath.
 */
public final class StaticFileHandler implements HttpHandler {
    private static final Map<String, Resource> RESOURCES = Map.of(
            "/", new Resource("/web/index.html", "text/html"),
            "/index.html", new Resource("/web/index.html", "text/html"),
            "/styles.css", new Resource("/web/styles.css", "text/css"),
            "/app.js", new Resource("/web/app.js", "text/javascript"));

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            HttpUtils.methodNotAllowed(exchange, "GET");
            return;
        }

        String rawPath = exchange.getRequestURI().getRawPath();
        String path = exchange.getRequestURI().getPath();
        if (rawPath.contains("..") || rawPath.contains("\\") || path.contains("..") || path.contains("\\")) {
            HttpUtils.sendJsonError(exchange, 404, "Resource not found");
            return;
        }

        Resource resource = RESOURCES.get(path);
        if (resource == null) {
            HttpUtils.sendJsonError(exchange, 404, "Resource not found");
            return;
        }

        try (InputStream input = StaticFileHandler.class.getResourceAsStream(resource.classpathLocation())) {
            if (input == null) {
                HttpUtils.sendJsonError(exchange, 404, "Resource not found");
                return;
            }
            HttpUtils.sendBytes(exchange, 200, resource.contentType() + "; charset=utf-8", input.readAllBytes());
        }
    }

    private record Resource(String classpathLocation, String contentType) {
    }
}
