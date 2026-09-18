package com.ivan.nexus.infrastructure.site;

import com.ivan.nexus.application.site.DomainHttpsProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "nexus.caddy.https-probe-enabled", havingValue = "true", matchIfMissing = true)
public class HttpClientDomainHttpsProbe implements DomainHttpsProbe {
    private static final Logger log = LoggerFactory.getLogger(HttpClientDomainHttpsProbe.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Override
    public boolean probe(String hostname) {
        if (hostname == null || hostname.isBlank()) {
            return false;
        }
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(TIMEOUT)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + hostname.trim() + "/"))
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            return status >= 200 && status < 500;
        } catch (IOException ex) {
            log.debug("HTTPS probe failed for {}: {}", hostname, ex.getMessage());
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
