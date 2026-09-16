package com.ivan.nexus.infrastructure.health;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class HttpHealthCheckerTest {

    private final HttpHealthChecker checker = new HttpHealthChecker();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void twoXxReturnsTrue() throws IOException {
        server = startServer(200);

        boolean healthy = checker.check(urlFor(server), Duration.ofSeconds(2));

        assertThat(healthy).isTrue();
    }

    @Test
    void fiveHundredReturnsFalse() throws IOException {
        server = startServer(500);

        boolean healthy = checker.check(urlFor(server), Duration.ofSeconds(2));

        assertThat(healthy).isFalse();
    }

    @Test
    void connectionRefusedReturnsFalse() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }
        String url = "http://127.0.0.1:" + port + "/";

        assertThatCode(() -> assertThat(checker.check(url, Duration.ofSeconds(2))).isFalse())
                .doesNotThrowAnyException();
    }

    private static HttpServer startServer(int status) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        httpServer.start();
        return httpServer;
    }

    private static String urlFor(HttpServer httpServer) {
        return "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/";
    }
}
