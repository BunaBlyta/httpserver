package com.networkproject.dashboard;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides the DNS and single-port TCP operations used by the API.
 */
public final class NetworkDiagnostics {
    public static final int CONNECT_TIMEOUT_MS = 1_500;

    private NetworkDiagnostics() {
    }

    public static DnsResult lookup(String host) {
        long started = System.nanoTime();
        try {
            Set<String> uniqueAddresses = new LinkedHashSet<>();
            Arrays.stream(InetAddress.getAllByName(host))
                    .map(InetAddress::getHostAddress)
                    .forEach(uniqueAddresses::add);
            return new DnsResult(true, host, List.copyOf(uniqueAddresses), elapsedMillis(started),
                    uniqueAddresses.isEmpty() ? "No addresses found" : "Host resolved successfully");
        } catch (UnknownHostException exception) {
            return new DnsResult(false, host, List.of(), elapsedMillis(started), "Host could not be resolved");
        }
    }

    public static PortCheckResult checkPort(String host, int port) {
        long started = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            return new PortCheckResult(true, host, port, true, elapsedMillis(started),
                    "TCP connection succeeded");
        } catch (IOException | IllegalArgumentException exception) {
            return new PortCheckResult(true, host, port, false, elapsedMillis(started),
                    "TCP connection could not be established");
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    public record DnsResult(boolean success, String host, List<String> addresses, long durationMs, String message) {
    }

    public record PortCheckResult(
            boolean success, String host, int port, boolean open, long durationMs, String message) {
    }
}
