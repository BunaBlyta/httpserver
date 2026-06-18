package com.networkproject.dashboard;

import java.io.IOException;
import java.net.BindException;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Command-line entry point for the network dashboard application.
 */
public final class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final int DEFAULT_PORT = 8080;

    private Main() {
    }

    public static void main(String[] args) {
        Integer port = parsePort(args);
        if (port == null) {
            return;
        }

        try {
            NetworkDashboardServer server = new NetworkDashboardServer(port);
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "dashboard-shutdown"));
            server.start();
            System.out.printf("Dashboard available at http://localhost:%d%n", server.getPort());
            awaitShutdown();
        } catch (IOException exception) {
            if (isBindFailure(exception)) {
                System.err.printf("Could not start the server: port %d is already in use.%n", port);
            } else {
                System.err.println("Could not start the server: " + safeMessage(exception));
            }
        }
    }

    private static Integer parsePort(String[] args) {
        if (args.length > 1) {
            System.err.println("Usage: java -jar target/network-dashboard.jar [port]");
            return null;
        }
        if (args.length == 0) {
            return DEFAULT_PORT;
        }

        try {
            int port = Integer.parseInt(args[0]);
            if (port < 1 || port > 65_535) {
                throw new NumberFormatException();
            }
            return port;
        } catch (NumberFormatException exception) {
            System.err.println("Invalid port. Enter a number from 1 through 65535.");
            return null;
        }
    }

    private static boolean isBindFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BindException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? "an unexpected startup error occurred" : exception.getMessage();
    }

    private static void awaitShutdown() {
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.FINE, "Main thread interrupted during shutdown", exception);
        }
    }
}
