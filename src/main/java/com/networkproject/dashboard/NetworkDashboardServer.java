package com.networkproject.dashboard;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the HTTP server, request executor, and application lifecycle.
 */
public final class NetworkDashboardServer implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(NetworkDashboardServer.class.getName());

    private final HttpServer httpServer;
    private final ExecutorService executor;
    private final Instant startupTime;
    private final AtomicLong requestCount = new AtomicLong();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Creates a localhost-only server. Port 0 requests an operating-system-assigned port.
     */
    public NetworkDashboardServer(int port) throws IOException {
        this(InetAddress.getByName("127.0.0.1"), port);
    }

    NetworkDashboardServer(InetAddress bindAddress, int port) throws IOException {
        Objects.requireNonNull(bindAddress, "bindAddress");
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }

        httpServer = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        executor = Executors.newFixedThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors()),
                Thread.ofPlatform().name("dashboard-worker-", 0).factory());
        httpServer.setExecutor(executor);
        startupTime = Instant.now();

        ApiHandlers apiHandlers = new ApiHandlers(startupTime, requestCount);
        register("/api/status", apiHandlers::handleStatus);
        register("/api/dns", apiHandlers::handleDns);
        register("/api/port-check", apiHandlers::handlePortCheck);
        register("/api/echo", apiHandlers::handleEcho);
        register("/", new StaticFileHandler());
    }

    private void register(String path, HttpHandler handler) {
        httpServer.createContext(path, exchange -> handleRequest(exchange, handler));
    }

    private void handleRequest(HttpExchange exchange, HttpHandler handler) throws IOException {
        requestCount.incrementAndGet();
        LOGGER.info(() -> exchange.getRequestMethod() + " " + exchange.getRequestURI());
        try {
            handler.handle(exchange);
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Request failed: " + exchange.getRequestURI(), exception);
            if (!exchange.getResponseHeaders().containsKey("Content-Type")) {
                HttpUtils.sendJsonError(exchange, 500, "Internal server error");
            } else {
                exchange.close();
            }
        }
    }

    /**
     * Starts accepting requests.
     */
    public void start() {
        if (closed.get()) {
            throw new IllegalStateException("Server has already been closed");
        }
        if (started.compareAndSet(false, true)) {
            httpServer.start();
            LOGGER.info(() -> "Server started on " + httpServer.getAddress());
        }
    }

    /**
     * Returns the actual bound port, including an automatically assigned test port.
     */
    public int getPort() {
        return httpServer.getAddress().getPort();
    }

    public long getRequestCount() {
        return requestCount.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        httpServer.stop(0);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("Server stopped");
    }
}
