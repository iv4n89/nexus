package com.ivan.nexus.infrastructure.health;

import com.ivan.nexus.application.deployment.HealthChecker;
import com.ivan.nexus.domain.manifest.HealthUrlPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpHealthChecker implements HealthChecker {
    private static final Logger log = LoggerFactory.getLogger(HttpHealthChecker.class);

    @Override
    public boolean check(String url, Duration timeout) {
        if (!HealthUrlPolicy.allowed(url)) {
            log.warn("Rejected health URL outside the private/loopback allowlist");
            return false;
        }
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(timeout)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .GET()
                .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            return status >= 200 && status < 300;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
