package com.ivan.nexus.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps failed {@code POST /api/auth/login} attempts per client IP.
 * Default: 10 failures / 10 minutes. Documented for operators; no Redis.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {
    static final int DEFAULT_MAX_FAILURES = 10;
    static final Duration DEFAULT_WINDOW = Duration.ofMinutes(10);

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginRateLimitFilter() {
        this(Clock.systemUTC(), DEFAULT_MAX_FAILURES, DEFAULT_WINDOW);
    }

    LoginRateLimitFilter(Clock clock, int maxFailures, Duration window) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isLogin(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String ip = ClientIp.resolve(request);
        prune(ip);
        Deque<Instant> recent = failures.get(ip);
        if (recent != null && recent.size() >= maxFailures) {
            reject(response);
            return;
        }
        filterChain.doFilter(request, response);
        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            failures.computeIfAbsent(ip, key -> new ArrayDeque<>()).addLast(clock.instant());
        } else if (response.getStatus() < 400) {
            failures.remove(ip);
        }
    }

    private void prune(String ip) {
        Deque<Instant> recent = failures.get(ip);
        if (recent == null) {
            return;
        }
        Instant cutoff = clock.instant().minus(window);
        while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
            recent.removeFirst();
        }
        if (recent.isEmpty()) {
            failures.remove(ip);
        }
    }

    private static boolean isLogin(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && "/api/auth/login".equals(request.getRequestURI());
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"error":{"code":"AUTH_INVALID","message":"Too many login attempts","timestamp":"%s"}}
                """.formatted(Instant.now()));
    }
}
